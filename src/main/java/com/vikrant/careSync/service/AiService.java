package com.vikrant.careSync.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.dto.GroqAiStructuredResponse;
import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiIntakeDraftResponse;
import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.dto.VisionScanResponse;
import com.vikrant.careSync.dto.CertificateReviewDto;
import com.vikrant.careSync.dto.ClinicalDictationResponse;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import com.vikrant.careSync.service.ai.AiConversationMemoryService;
import com.vikrant.careSync.service.ai.GeminiClient;
import com.vikrant.careSync.service.ai.GroqClient;
import com.vikrant.careSync.service.ai.AiQuotaExceededException;
import com.vikrant.careSync.service.ai.AiUsage;
import com.vikrant.careSync.service.ai.agent.AiAgentService;
import com.vikrant.careSync.service.ai.AiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import com.vikrant.careSync.service.ai.AiAuditService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final GroqClient groqClient;
    private final AiBookingService aiBookingService;
    private final AiClinicalService aiClinicalService;
    private final AiConversationMemoryService memoryService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AiAuditService aiAuditService;
    private final GeminiClient geminiClient;
    private final AccessGuard accessGuard;
    private final AiAgentService aiAgentService;
    private final SupabaseStorageService supabaseStorageService;

    public AiChatResponse getResponse(AiChatRequest request) {
        long startTime = System.currentTimeMillis();
        groqClient.resetLastCall();
        String bookingActionProduced = "NONE";
        boolean success = false;
        String errorMessage = null;
        String userMessage = request != null ? request.getMessage() : null;
        try {
            AiChatResponse finalResponse = null;

            // Conversation ID: reuse a well-formed id from the client, otherwise start a new conversation
            String conversationId = validConversationIdOrNew(request.getConversationId());
            boolean turnSaved = false;
            String chatModel = null;
            AiUsage chatUsage = null;
            List<String> toolsUsed = List.of();

            // 1. Emergency Triage Safety Check
            if (isEmergencySymptom(userMessage)) {
                finalResponse = handleEmergencyTriage(userMessage);
                finalResponse.setConversationId(conversationId);
            } else {
                // 2. Handle UI-triggered action commands
                if (userMessage.equals("ACTION_GET_SPECIALIZATIONS")) {
                    finalResponse = aiBookingService.handleGetSpecializations();
                } else if (userMessage.startsWith("ACTION_SELECT_SPECIALIZATION_")) {
                    finalResponse = aiBookingService.handleSpecializationSelection(userMessage);
                } else if (userMessage.startsWith("ACTION_SELECT_DOCTOR_")) {
                    finalResponse = aiBookingService.handleDoctorSelection(userMessage);
                } else if (userMessage.startsWith("ACTION_SELECT_DATE_")) {
                    finalResponse = aiBookingService.handleDateSelection(userMessage);
                } else if (userMessage.startsWith("ACTION_SELECT_SLOT_")) {
                    finalResponse = aiBookingService.handleSlotSelection(userMessage);
                } else if (userMessage.startsWith("ACTION_CANCEL_APPOINTMENT_")) {
                    finalResponse = aiBookingService.handleCancelAppointment(userMessage);
                } else if (userMessage.startsWith("ACTION_START_RESCHEDULE_")) {
                    finalResponse = aiBookingService.handleStartReschedule(userMessage);
                }

                if (finalResponse != null) {
                    finalResponse.setConversationId(conversationId);
                } else {
                    boolean isDoctor = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                            .stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));

                    if (!isDoctor) {
                        // 3. Patients: the AI agent. Its tools run on the server as this patient; bookings,
                        // cancellations and reschedules only produce a card the patient confirms by clicking.
                        AiAgentService.AgentReply reply = aiAgentService.run(userMessage,
                                memoryService.getRecentHistory(conversationId));
                        finalResponse = AiChatResponse.builder().response(reply.text()).success(true)
                                .suggestion(reply.card()).conversationId(conversationId).build();
                        memoryService.saveTurn(conversationId, userMessage,
                                reply.text() + AiAgentService.cardNote(reply.card()));
                        turnSaved = true;
                        chatModel = reply.model();
                        chatUsage = reply.usage();
                        toolsUsed = reply.toolsUsed();
                    } else {
                        // 4. Doctors: clinical assistant (patient context via name; a doctor co-pilot comes later)

                        String systemInstructions = "You are CareSync AI Clinical Assistant, a specialized advisor for medical professionals. "
                                    + "Respond strictly in JSON format with fields:\n"
                                    + "{\"reply\": \"User-facing response\", \"intent\": \"GENERAL_CONSULT\", \"specializations\": [], \"action\": \"NONE\"}.\n"
                                    + "- For clinical topics: provide evidence-based insights.\n"
                                    + "- Always include a medical disclaimer in 'reply'.";

                        String context = "";
                        if ((userMessage.toLowerCase().contains("summarize")
                                || userMessage.toLowerCase().contains("history"))) {
                            // Only the doctor's own patients can be summarized
                            List<Patient> patients = patientRepository.findAllById(accessGuard.treatedPatientIds());
                            for (Patient p : patients) {
                                if (userMessage.toLowerCase().contains(p.getName().toLowerCase())) {
                                    MedicalSummaryResponse summary = summarizePatientHistory(p.getId());
                                    if (summary.isSuccess()) {
                                        context = "\n[CONTEXT] Patient " + p.getName() + " History Summary: "
                                                + summary.getSummary() + "\n";
                                        break;
                                    }
                                }
                            }
                        }

                        List<Map<String, String>> conversationHistory = memoryService.getRecentHistory(conversationId);
                        String rawJson = groqClient.callGroq(systemInstructions + context, conversationHistory,
                                userMessage, true, conversationId);

                        if (rawJson != null && !rawJson.isBlank()) {
                            try {
                                String cleanJson = extractJson(rawJson);
                                GroqAiStructuredResponse structured = new ObjectMapper().readValue(cleanJson,
                                        GroqAiStructuredResponse.class);

                                if (structured != null) {
                                    String reply = structured.getReply();
                                    String intent = structured.getIntent() != null ? structured.getIntent()
                                            : "GENERAL_CONSULT";
                                    String action = structured.getAction() != null ? structured.getAction() : "NONE";

                                    if ("CANCEL_APPOINTMENT".equalsIgnoreCase(intent)
                                            || "SHOW_MY_APPOINTMENTS".equalsIgnoreCase(action)) {
                                        finalResponse = aiBookingService.handleFetchMyAppointments(
                                                "Which appointment would you like to cancel?", reply);
                                    } else if ("RESCHEDULE_APPOINTMENT".equalsIgnoreCase(intent)) {
                                        finalResponse = aiBookingService.handleFetchMyAppointments(
                                                "Which appointment would you like to move?", reply);
                                    } else if ("BOOK_APPOINTMENT".equalsIgnoreCase(intent)
                                            || "SHOW_DOCTORS".equalsIgnoreCase(action)
                                            || (structured.getSpecializations() != null
                                                    && !structured.getSpecializations().isEmpty())) {
                                        finalResponse = aiBookingService.handleRecommendedSpecializationList(
                                                structured.getSpecializations(), reply, request.getMessage());
                                    } else {
                                        finalResponse = AiChatResponse.builder().response(reply).success(true).build();
                                    }

                                    finalResponse.setConversationId(conversationId);
                                    memoryService.saveTurn(conversationId, userMessage, reply);
                                    turnSaved = true;
                                }
                            } catch (Exception parseEx) {
                                log.warn("Failed to parse structured JSON response: {}", parseEx.getMessage());
                                finalResponse = AiChatResponse.builder().response(rawJson)
                                        .conversationId(conversationId).success(true).build();
                            }
                        }
                    }
                }
            }

            if (finalResponse == null) {
                finalResponse = AiChatResponse.builder().conversationId(conversationId).success(false)
                        .error("Unable to generate AI response.").build();
            }

            success = finalResponse.isSuccess();
            if (finalResponse.getSuggestion() != null && finalResponse.getSuggestion().getType() != null) {
                bookingActionProduced = finalResponse.getSuggestion().getType().name();
            }

            // Save the turn once (the model path saves above). Button commands (ACTION_*) are not conversation, so
            // they stay out of the model's memory.
            if (!turnSaved && finalResponse.getResponse() != null) {
                if (userMessage.startsWith("ACTION_")) {
                    memoryService.saveTurn(conversationId, "(The patient used a button in the chat)",
                            finalResponse.getResponse() + AiAgentService.cardNote(finalResponse.getSuggestion()));
                } else {
                    memoryService.saveTurn(conversationId, userMessage, finalResponse.getResponse());
                }
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            String responseJson = null;
            try {
                if (finalResponse != null) {
                    responseJson = new ObjectMapper().writeValueAsString(finalResponse);
                }
            } catch (Exception ex) {
                responseJson = finalResponse != null ? finalResponse.getResponse() : null;
            }
            AiUsage usage = chatUsage != null ? chatUsage : groqClient.getLastUsage();
            String auditedAction = toolsUsed.isEmpty() ? bookingActionProduced
                    : bookingActionProduced + " via " + String.join(",", toolsUsed);
            aiAuditService.logInteraction("CHAT", chatModel != null ? chatModel : groqClient.getLastUsedModel(),
                    latencyMs, success, usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
                    auditedAction.length() > 250 ? auditedAction.substring(0, 250) : auditedAction,
                    errorMessage, userMessage, responseJson);

            return finalResponse;
        } catch (AiQuotaExceededException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("CHAT", null, latencyMs, false,
                    null, null, null, "NONE", "AI allowance used up", userMessage, null);
            return AiChatResponse.builder().response(e.userMessage()).success(false).build();
        } catch (Exception e) {
            log.error("AI Error: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("CHAT", groqClient.getLastUsedModel(), latencyMs, false,
                    null, null, null, "NONE", errorMessage, userMessage, null);

            return AiChatResponse.builder()
                    .response("Sorry, the AI assistant is unavailable right now. Please try again in a moment.")
                    .success(false)
                    .build();
        }
    }

    public void streamResponse(AiChatRequest request, java.util.function.Consumer<String> chunkConsumer) {
        long startTime = System.currentTimeMillis();
        groqClient.resetLastCall();
        boolean success = true;
        String errorMessage = null;
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        String userMessage = request.getMessage();
        List<Map<String, String>> conversationHistory = memoryService.getRecentHistory(conversationId);
        StringBuilder fullResponse = new StringBuilder();

        String specializationsList = String.join(", ", aiBookingService.getAvailableSpecializations());
        String systemInstructions = "You are CareSync AI health assistant. "
                + "Provide concise, empathetic medical and appointment guidance. "
                + "Available specializations: [" + specializationsList + "].";

        final String activeConversationId = conversationId;
        try {
            groqClient.streamGroq(systemInstructions, conversationHistory, userMessage, false, activeConversationId,
                    chunk -> {
                        fullResponse.append(chunk);
                        chunkConsumer.accept(chunk);
                    });

            if (fullResponse.length() > 0) {
                memoryService.saveTurn(activeConversationId, userMessage, fullResponse.toString());
            }
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("STREAM", groqClient.getModel(), latencyMs, success,
                    null, null, null, "STREAM_CHAT", errorMessage, userMessage, fullResponse.toString());
        }
    }

    /** Client-supplied ids are reused only when they look like ours (letters, digits, dashes); otherwise a new one */
    static String validConversationIdOrNew(String conversationId) {
        return conversationId != null && conversationId.matches("[A-Za-z0-9-]{8,64}")
                ? conversationId
                : UUID.randomUUID().toString();
    }

    private String extractJson(String text) {
        return com.vikrant.careSync.service.ai.AiJson.extractObject(text);
    }

    public MedicalSummaryResponse summarizePatientHistory(Long patientId) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        MedicalSummaryResponse response = null;
        try {
            response = aiClinicalService.withCallerAudit(() -> aiClinicalService.summarizePatientHistory(patientId));
            if (response != null) {
                success = response.isSuccess();
                if (!success)
                    errorMessage = response.getError();
            }
            return response;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            String promptText = "Summarize Patient History for patient ID: " + patientId;
            String respText = null;
            try {
                if (response != null) {
                    respText = new ObjectMapper().writeValueAsString(response);
                }
            } catch (Exception ex) {
                respText = response != null ? response.getSummary() : null;
            }
            AiUsage usage = aiClinicalService.lastUsage();
            aiAuditService.logInteraction("SUMMARIZE", aiClinicalService.lastModel(), latencyMs, success,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), "SUMMARIZE_PATIENT",
                    errorMessage, promptText, respText);
        }
    }

    public DiagnosisSuggestionDto suggestDiagnosis(String symptoms) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        DiagnosisSuggestionDto response = null;
        try {
            response = aiClinicalService.withCallerAudit(() -> aiClinicalService.suggestDiagnosis(symptoms));
            if (response == null || response.getSuggestions() == null) {
                success = false;
                errorMessage = "No diagnosis suggestions returned";
            }
            return response;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            String respText = null;
            try {
                if (response != null) {
                    respText = new ObjectMapper().writeValueAsString(response);
                }
            } catch (Exception ex) {
                respText = null;
            }
            AiUsage usage = aiClinicalService.lastUsage();
            aiAuditService.logInteraction("SUGGEST_DIAGNOSIS", aiClinicalService.lastModel(), latencyMs, success,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), "SUGGEST_DIAGNOSIS",
                    errorMessage, symptoms, respText);
        }
    }

    public AiIntakeDraftResponse draftIntake(String narrative) {
        return aiClinicalService.draftIntakeFromNarrative(narrative);
    }

    private boolean isEmergencySymptom(String message) {
        if (message == null)
            return false;
        String lower = message.toLowerCase();
        return lower.contains("chest pain") || lower.contains("heart attack") || lower.contains("shortness of breath")
                || lower.contains("can't breathe") || lower.contains("cant breathe")
                || lower.contains("severe bleeding")
                || lower.contains("stroke") || lower.contains("unconscious") || lower.contains("suicide")
                || lower.contains("kill myself");
    }

    private AiChatResponse handleEmergencyTriage(String userMsg) {
        String warningMessage = "**URGENT MEDICAL NOTICE**: Your message indicates a potential medical emergency.\n\n"
                + "Please call emergency services immediately (911 / 112 / 102) or go to the nearest hospital Emergency Room.\n"
                + "Do not wait for an online appointment for severe symptoms such as chest pain, severe shortness of breath, stroke symptoms, or severe bleeding.\n\n"
                + "If you still need an urgent doctor consultation, choose from available Emergency & Cardiology specialists below:";

        List<Doctor> emergencyDoctors = doctorRepository.findAll().stream()
                .filter(Doctor::canAcceptAppointments)
                .filter(d -> "Cardiology".equalsIgnoreCase(d.getSpecialization())
                        || "Emergency Medicine".equalsIgnoreCase(d.getSpecialization()))
                .collect(Collectors.toList());

        return AiChatResponse.builder()
                .response(warningMessage)
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                        .doctors(emergencyDoctors.stream().map(aiBookingService::mapToDoctorSuggestion)
                                .collect(Collectors.toList()))
                        .reason("EMERGENCY_TRIAGE")
                        .build())
                .build();
    }

    public VisionScanResponse scanMedicalDocument(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        geminiClient.resetLastCall();
        boolean success = true;
        String errorMessage = null;
        VisionScanResponse scanResponse = null;

        if (file == null || file.isEmpty()) {
            return VisionScanResponse.builder()
                    .success(false)
                    .error("No file uploaded for vision scanning.")
                    .build();
        }

        try {
            byte[] fileBytes = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/jpeg";
            }

            String prompt = "You are a specialized medical Vision AI assistant.\n"
                    + "Analyze this medical document (prescription, lab report, or clinical note) carefully.\n"
                    + "Extract all details and return STRICTLY valid JSON matching this schema:\n"
                    + "{\n"
                    + "  \"documentType\": \"PRESCRIPTION\" | \"LAB_REPORT\" | \"MEDICAL_NOTE\" | \"UNKNOWN\",\n"
                    + "  \"patientName\": \"Extracted patient name or null\",\n"
                    + "  \"doctorName\": \"Extracted doctor name or null\",\n"
                    + "  \"date\": \"YYYY-MM-DD or date string\",\n"
                    + "  \"medications\": [\n"
                    + "     { \"name\": \"Medication Name\", \"dosage\": \"e.g. 500mg\", \"frequency\": \"e.g. Twice daily\", \"duration\": \"e.g. 7 days\", \"instructions\": \"e.g. Take after meals\" }\n"
                    + "  ],\n"
                    + "  \"labResults\": [\n"
                    + "     { \"testName\": \"e.g. Hemoglobin\", \"resultValue\": \"e.g. 14.2 g/dL\", \"referenceRange\": \"13.5 - 17.5 g/dL\", \"status\": \"NORMAL\" | \"HIGH\" | \"LOW\" | \"ABNORMAL\" }\n"
                    + "  ],\n"
                    + "  \"rawSummary\": \"A clear executive summary of the document\",\n"
                    + "  \"warnings\": [\"List any medical abnormalities or safety warnings flagged in the document\"]\n"
                    + "}\n"
                    + "Rules:\n"
                    + "1. Do not include markdown code fence formatting (no ```json).\n"
                    + "2. If a field is missing, use null or an empty list [].\n"
                    + "3. Format lab result status accurately (HIGH/LOW/NORMAL/ABNORMAL).";

            String visionResult = null;
            if (geminiClient != null && geminiClient.isConfigured()) {
                log.info("Analyzing medical document with Gemini ({})", geminiClient.getModel());
                visionResult = geminiClient.generateVisionContent(fileBytes, contentType, prompt);
            } else {
                log.warn("Gemini Vision AI is not configured. Returning default fallback summary.");
                visionResult = "{\"documentType\":\"UNKNOWN\",\"rawSummary\":\"Vision AI engine requires Gemini API key configuration.\",\"medications\":[],\"labResults\":[],\"warnings\":[\"AI Vision Key not configured\"]}";
            }

            if (visionResult != null && !visionResult.isBlank()) {
                String cleanJson = extractJson(visionResult);
                ObjectMapper mapper = new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                scanResponse = mapper.readValue(cleanJson, VisionScanResponse.class);
                scanResponse.setSuccess(true);
            }
        } catch (Exception e) {
            log.error("Vision AI scan failed: {}", e.getMessage(), e);
            success = false;
            errorMessage = e.getMessage();
            scanResponse = VisionScanResponse.builder()
                    .success(false)
                    .error(e instanceof AiQuotaExceededException quota ? quota.userMessage()
                            : "Could not read this document right now. Please try again.")
                    .build();
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            AiUsage usage = geminiClient.getLastUsage();
            aiAuditService.logInteraction("VISION_SCAN", geminiClient.getModel(), latencyMs, success,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), "VISION_DOCUMENT_SCAN",
                    errorMessage, file.getOriginalFilename(),
                    scanResponse != null ? scanResponse.getRawSummary() : null);
        }

        return scanResponse != null ? scanResponse : VisionScanResponse.builder().success(false).error("Unable to parse document").build();
    }

    public ClinicalDictationResponse structureClinicalDictation(String transcript) {
        long startTime = System.currentTimeMillis();
        groqClient.resetLastCall();
        geminiClient.resetLastCall();
        boolean success = true;
        String errorMessage = null;
        String dictationModel = "none";
        AiUsage dictationUsage = AiUsage.NONE;
        ClinicalDictationResponse dictationResponse = null;

        if (transcript == null || transcript.isBlank()) {
            return ClinicalDictationResponse.builder()
                    .success(false)
                    .error("Dictation transcript cannot be empty.")
                    .build();
        }

        try {
            String prompt = "You are CareSync AI Clinical Dictation Specialist. Analyze the following spoken doctor consultation notes and structure them into JSON.\n\n"
                    + "Spoken Consultation Notes:\n\"" + transcript + "\"\n\n"
                    + "Respond strictly with valid JSON conforming to this schema:\n"
                    + "{\n"
                    + "  \"patientName\": \"Patient Name if mentioned or null\",\n"
                    + "  \"chiefComplaint\": \"Primary symptoms & chief complaint\",\n"
                    + "  \"vitals\": \"Blood pressure, pulse, temperature, SpO2 if mentioned or null\",\n"
                    + "  \"diagnosis\": \"Clinical diagnosis or diagnostic impression\",\n"
                    + "  \"prescriptions\": [\n"
                    + "     { \"name\": \"Medication Name\", \"dosage\": \"500mg\", \"frequency\": \"Twice daily\", \"duration\": \"7 days\", \"instructions\": \"After meals\" }\n"
                    + "  ],\n"
                    + "  \"labOrders\": [\"Recommended lab test or imaging\"],\n"
                    + "  \"followUp\": \"Follow-up timeframe or instructions\",\n"
                    + "  \"soapNote\": {\n"
                    + "     \"subjective\": \"Patient history, reported symptoms and history of present illness\",\n"
                    + "     \"objective\": \"Physical exam findings, vitals, and lab observations\",\n"
                    + "     \"assessment\": \"Clinical diagnosis and differential assessment\",\n"
                    + "     \"plan\": \"Treatment plan, prescriptions, patient education, and follow-up\"\n"
                    + "  }\n"
                    + "}\n"
                    + "Rules:\n"
                    + "1. Return strictly valid JSON. Do not include markdown formatting or explanations.\n"
                    + "2. If a section was not mentioned in dictation, provide a reasonable clinical inference or null.";

            String aiResult = null;
            if (groqClient != null && groqClient.isConfigured()) {
                aiResult = groqClient.callGroq("You are CareSync AI Clinical Dictation Specialist.", prompt, true);
                dictationModel = groqClient.getLastUsedModel();
                dictationUsage = groqClient.getLastUsage();
            } else if (geminiClient != null && geminiClient.isConfigured()) {
                aiResult = geminiClient.generateContent(prompt);
                dictationModel = geminiClient.getModel();
                dictationUsage = geminiClient.getLastUsage();
            } else {
                // No AI configured: return the dictation as-is; never invent vitals or an assessment
                aiResult = new ObjectMapper().writeValueAsString(java.util.Map.of(
                        "chiefComplaint", transcript,
                        "prescriptions", java.util.List.of(),
                        "labOrders", java.util.List.of(),
                        "soapNote", java.util.Map.of("subjective", transcript)));
            }

            if (aiResult != null && !aiResult.isBlank()) {
                String cleanJson = extractJson(aiResult);
                ObjectMapper mapper = new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                dictationResponse = mapper.readValue(cleanJson, ClinicalDictationResponse.class);
                dictationResponse.setSuccess(true);
            }
        } catch (Exception e) {
            log.error("Clinical dictation structuring failed: {}", e.getMessage(), e);
            success = false;
            errorMessage = e.getMessage();
            dictationResponse = ClinicalDictationResponse.builder()
                    .success(false)
                    .error(e instanceof AiQuotaExceededException quota ? quota.userMessage()
                            : "Could not structure the dictation right now. Please try again.")
                    .build();
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("CLINICAL_DICTATION", dictationModel, latencyMs, success,
                    dictationUsage.promptTokens(), dictationUsage.completionTokens(), dictationUsage.totalTokens(),
                    "CLINICAL_DICTATION", errorMessage, transcript,
                    dictationResponse != null ? dictationResponse.getDiagnosis() : null);
        }

        return dictationResponse != null ? dictationResponse : ClinicalDictationResponse.builder().success(false).error("Unable to process dictation").build();
    }

    public CertificateReviewDto reviewCertificate(Doctor doctor, Certificate certificate) {
        List<String> mismatches = new ArrayList<>();
        if (certificate.getName() == null || certificate.getName().isBlank()) {
            mismatches.add("Certificate name is missing from the doctor profile.");
        }
        if (certificate.getIssuingOrganization() == null || certificate.getIssuingOrganization().isBlank()) {
            mismatches.add("Issuing organization is missing from the doctor profile.");
        }
        if (certificate.getCredentialId() == null || certificate.getCredentialId().isBlank()) {
            mismatches.add("Credential ID is missing from the doctor profile.");
        }
        if (certificate.getExpiryDate() != null && certificate.getExpiryDate().isBefore(LocalDate.now())) {
            mismatches.add("The recorded expiry date is in the past.");
        }
        if (certificate.getIssueDate() != null && certificate.getExpiryDate() != null
                && certificate.getIssueDate().isAfter(certificate.getExpiryDate())) {
            mismatches.add("The recorded issue date is after the expiry date.");
        }

        String holder = "";
        String organization = "";
        String credentialId = "";
        String issueDate = "";
        String expiryDate = "";
        byte[] file = downloadCertificate(certificate.getUrl());
        if (file == null) {
            mismatches.add("The certificate file is not stored in CareSync or could not be read. "
                    + "Review the document manually.");
        } else if (geminiClient == null || !geminiClient.isConfigured()) {
            mismatches.add("Automatic certificate reading is unavailable. Review the document manually.");
        } else {
            try {
                String prompt = "Extract only details visibly printed on this medical certificate or license. "
                        + "Do not infer missing values. Return JSON: {\"holderName\":\"\",\"organization\":\"\","
                        + "\"credentialId\":\"\",\"issueDate\":\"\",\"expiryDate\":\"\"}.";
                String raw = geminiClient.generateVisionContent(file, mimeType(certificate.getUrl()), prompt);
                com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readTree(extractJson(raw));
                holder = node.path("holderName").asText("");
                organization = node.path("organization").asText("");
                credentialId = node.path("credentialId").asText("");
                issueDate = node.path("issueDate").asText("");
                expiryDate = node.path("expiryDate").asText("");
                if (!holder.isBlank() && doctor.getName() != null && !doctor.getName().isBlank()
                        && !namesMatch(doctor.getName(), holder)) {
                    mismatches.add("Extracted holder name does not match " + doctor.getName() + ".");
                }
                if (!organization.isBlank() && certificate.getIssuingOrganization() != null
                        && !certificate.getIssuingOrganization().isBlank()
                        && !containsEither(organization, certificate.getIssuingOrganization())) {
                    mismatches.add("Extracted issuing organization does not match the recorded organization.");
                }
                if (!credentialId.isBlank() && certificate.getCredentialId() != null
                        && !certificate.getCredentialId().isBlank()
                        && !credentialId.equalsIgnoreCase(certificate.getCredentialId())) {
                    mismatches.add("Extracted credential ID does not match the recorded credential ID.");
                }
            } catch (Exception e) {
                log.warn("Certificate extraction failed: {}", e.getMessage());
                mismatches.add("Certificate details could not be extracted. Review the document manually.");
            }
        }

        return CertificateReviewDto.builder()
                .certificateId(certificate.getId())
                .extractedHolderName(holder)
                .extractedOrganization(organization)
                .extractedCredentialId(credentialId)
                .extractedIssueDate(issueDate)
                .extractedExpiryDate(expiryDate)
                .mismatches(mismatches)
                .adminApprovalRequired(true)
                .build();
    }

    /**
     * Only certificate files uploaded to CareSync storage are read, through the storage API. A link the doctor
     * typed in is never fetched: that would let anyone make the server call internal addresses (SSRF).
     */
    private byte[] downloadCertificate(String url) {
        return supabaseStorageService
                .readOwnFile(url, SupabaseStorageService.FileType.CERTIFICATE, 8_000_000L)
                .orElse(null);
    }

    private String mimeType(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".pdf")) return "application/pdf";
        if (lower.contains(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private boolean namesMatch(String doctorName, String extractedName) {
        String extracted = extractedName.toLowerCase().replace("dr.", "");
        for (String part : doctorName.toLowerCase().replace("dr.", "").split("\\s+")) {
            if (part.length() > 2 && extracted.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEither(String left, String right) {
        String a = left.toLowerCase();
        String b = right.toLowerCase();
        return a.contains(b) || b.contains(a);
    }
}
