package com.vikrant.careSync.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.GroqAiStructuredResponse;
import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.dto.VisionScanResponse;
import com.vikrant.careSync.dto.ClinicalDictationResponse;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import com.vikrant.careSync.service.ai.AiConversationMemoryService;
import com.vikrant.careSync.service.ai.GeminiClient;
import com.vikrant.careSync.service.ai.GroqClient;
import com.vikrant.careSync.service.ai.GrokClient;
import com.vikrant.careSync.service.ai.AiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final GrokClient grokClient;
    private final AiBookingService aiBookingService;
    private final AiClinicalService aiClinicalService;
    private final AiConversationMemoryService memoryService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AiAuditService aiAuditService;
    private final GeminiClient geminiClient;

    public AiChatResponse getResponse(AiChatRequest request) {
        long startTime = System.currentTimeMillis();
        String bookingActionProduced = "NONE";
        boolean success = false;
        String errorMessage = null;
        String userMessage = request != null ? request.getMessage() : null;
        try {
            AiChatResponse finalResponse = null;

            // Conversation ID generation/retrieval
            String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                    ? request.getConversationId()
                    : UUID.randomUUID().toString();

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
                    String lowerMsg = userMessage.toLowerCase();

                    // 3. Direct Specialization mentions
                    List<String> specializations = aiBookingService.getAvailableSpecializations();
                    for (String spec : specializations) {
                        if (lowerMsg.contains(spec.toLowerCase())) {
                            finalResponse = aiBookingService
                                    .handleSpecializationSelection("ACTION_SELECT_SPECIALIZATION_" + spec);
                            finalResponse.setConversationId(conversationId);
                            break;
                        }
                    }

                    // 4. Direct Doctor Name mentions
                    if (finalResponse == null) {
                        List<Doctor> allDoctors = doctorRepository.findAll();
                        for (Doctor d : allDoctors) {
                            String fullName = (d.getName() != null ? d.getName() : "").toLowerCase();
                            String lastName = (d.getLastName() != null ? d.getLastName() : "").toLowerCase();
                            if (!fullName.isEmpty() && lowerMsg.contains(fullName)) {
                                finalResponse = aiBookingService
                                        .handleDoctorSelection("ACTION_SELECT_DOCTOR_" + d.getId());
                                finalResponse.setConversationId(conversationId);
                                break;
                            } else if (!lastName.isEmpty() && lowerMsg.contains(lastName)) {
                                finalResponse = aiBookingService
                                        .handleDoctorSelection("ACTION_SELECT_DOCTOR_" + d.getId());
                                finalResponse.setConversationId(conversationId);
                                break;
                            }
                        }
                    }

                    // 5. Check if user sent direct date/time text or let Grok/Groq handle
                    // structured intent
                    // (Naive keyword matching for "book" was removed to prevent hijacking natural
                    // language inputs like "book an appointment with her at 10am tomorrow")

                    // 6. Groq / xAI Fallback Call
                    if (finalResponse == null) {
                        boolean isDoctor = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));

                        String specializationsList = String.join(", ", aiBookingService.getAvailableSpecializations());
                        String systemInstructions;

                        if (isDoctor) {
                            systemInstructions = "You are CareSync AI Clinical Assistant, a specialized advisor for medical professionals. "
                                    + "Respond strictly in JSON format with fields:\n"
                                    + "{\"reply\": \"User-facing response\", \"intent\": \"GENERAL_CONSULT\", \"specializations\": [], \"action\": \"NONE\"}.\n"
                                    + "- For clinical topics: provide evidence-based insights.\n"
                                    + "- Always include a medical disclaimer in 'reply'.";
                        } else {
                            systemInstructions = "You are CareSync AI health assistant powered by Groq/xAI. "
                                    + "Respond STRICTLY in JSON format matching this schema:\n" + "{\n"
                                    + "  \"reply\": \"Detailed user-facing empathetic health advice and medical disclaimer\",\n"
                                    + "  \"intent\": \"BOOK_APPOINTMENT\" | \"CANCEL_APPOINTMENT\" | \"RESCHEDULE_APPOINTMENT\" | \"GENERAL_CONSULT\",\n"
                                    + "  \"specializations\": [\"SpecializationFromList\"],\n"
                                    + "  \"action\": \"SHOW_DOCTORS\" | \"SHOW_SPECIALIZATIONS\" | \"SHOW_MY_APPOINTMENTS\" | \"NONE\"\n"
                                    + "}\n" + "Rules:\n" + "1. Available specializations: [" + specializationsList
                                    + "]\n"
                                    + "2. If user has symptoms: set intent='BOOK_APPOINTMENT', specializations=[matching specializations], action='SHOW_DOCTORS'.\n"
                                    + "3. If user wants to cancel appointment: set intent='CANCEL_APPOINTMENT', action='SHOW_MY_APPOINTMENTS'.\n"
                                    + "4. If user wants to move/reschedule: set intent='RESCHEDULE_APPOINTMENT', action='SHOW_MY_APPOINTMENTS'.\n"
                                    + "5. Always include a concise medical disclaimer inside 'reply'.";
                        }

                        String context = "";
                        if (isDoctor && (userMessage.toLowerCase().contains("summarize")
                                || userMessage.toLowerCase().contains("history"))) {
                            List<Patient> patients = patientRepository.findAll();
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
                        String rawJson = grokClient.callGrok(systemInstructions + context, conversationHistory,
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

            // Save turn to memory if not saved earlier
            if (finalResponse.getResponse() != null) {
                memoryService.saveTurn(conversationId, userMessage, finalResponse.getResponse());
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
            aiAuditService.logInteraction("CHAT", grokClient.getLastUsedModel(), latencyMs, success,
                    null, null, null, bookingActionProduced, errorMessage, userMessage, responseJson);

            return finalResponse;
        } catch (Exception e) {
            log.error("AI Error: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("CHAT", grokClient.getLastUsedModel(), latencyMs, false,
                    null, null, null, "NONE", errorMessage, userMessage, null);

            return AiChatResponse.builder()
                    .response("I apologize, but I encountered an error processing your request: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    public void streamResponse(AiChatRequest request, java.util.function.Consumer<String> chunkConsumer) {
        long startTime = System.currentTimeMillis();
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
        String systemInstructions = "You are CareSync AI health assistant powered by Grok/xAI. "
                + "Provide concise, empathetic medical and appointment guidance. "
                + "Available specializations: [" + specializationsList + "].";

        final String activeConversationId = conversationId;
        try {
            grokClient.streamGrok(systemInstructions, conversationHistory, userMessage, false, activeConversationId,
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
            aiAuditService.logInteraction("STREAM", grokClient.getModel(), latencyMs, success,
                    null, null, null, "STREAM_CHAT", errorMessage, userMessage, fullResponse.toString());
        }
    }

    private String extractJson(String text) {
        if (text == null)
            return "{}";
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    public MedicalSummaryResponse summarizePatientHistory(Long patientId) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        MedicalSummaryResponse response = null;
        try {
            response = aiClinicalService.summarizePatientHistory(patientId);
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
            aiAuditService.logInteraction("SUMMARIZE", grokClient.getModel(), latencyMs, success,
                    null, null, null, "SUMMARIZE_PATIENT", errorMessage, promptText, respText);
        }
    }

    public DiagnosisSuggestionDto suggestDiagnosis(String symptoms) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        DiagnosisSuggestionDto response = null;
        try {
            response = aiClinicalService.suggestDiagnosis(symptoms);
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
            aiAuditService.logInteraction("SUGGEST_DIAGNOSIS", grokClient.getModel(), latencyMs, success,
                    null, null, null, "SUGGEST_DIAGNOSIS", errorMessage, symptoms, respText);
        }
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
                log.info("Analyzing medical document using Gemini 1.5 Flash Vision AI...");
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
                    .error("Failed to scan document: " + e.getMessage())
                    .build();
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("VISION_SCAN", "gemini-1.5-flash", latencyMs, success,
                    null, null, null, "VISION_DOCUMENT_SCAN", errorMessage, file.getOriginalFilename(),
                    scanResponse != null ? scanResponse.getRawSummary() : null);
        }

        return scanResponse != null ? scanResponse : VisionScanResponse.builder().success(false).error("Unable to parse document").build();
    }

    public ClinicalDictationResponse structureClinicalDictation(String transcript) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
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
            if (grokClient != null && grokClient.isConfigured()) {
                log.info("Structuring doctor dictation notes using Groq/xAI LLM...");
                aiResult = grokClient.callGrok("You are CareSync AI Clinical Dictation Specialist.", prompt, true);
            } else if (geminiClient != null && geminiClient.isConfigured()) {
                log.info("Structuring doctor dictation notes using Gemini 1.5 Flash LLM...");
                aiResult = geminiClient.generateVisionContent(null, null, prompt);
            } else {
                aiResult = "{\"chiefComplaint\":\"" + transcript + "\",\"diagnosis\":\"Clinical evaluation required\",\"prescriptions\":[],\"labOrders\":[],\"soapNote\":{\"subjective\":\"" + transcript + "\",\"objective\":\"Vitals within range\",\"assessment\":\"Clinical evaluation\",\"plan\":\"Standard follow-up\"}}";
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
                    .error("Failed to structure dictation: " + e.getMessage())
                    .build();
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            aiAuditService.logInteraction("CLINICAL_DICTATION", "groq-llama-3.3-70b", latencyMs, success,
                    null, null, null, "CLINICAL_DICTATION", errorMessage, transcript,
                    dictationResponse != null ? dictationResponse.getDiagnosis() : null);
        }

        return dictationResponse != null ? dictationResponse : ClinicalDictationResponse.builder().success(false).error("Unable to process dictation").build();
    }
}
