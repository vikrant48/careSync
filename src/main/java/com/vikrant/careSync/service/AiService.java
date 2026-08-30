package com.vikrant.careSync.service;

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
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import com.vikrant.careSync.service.ai.AiConversationMemoryService;
import com.vikrant.careSync.service.ai.GroqClient;
import com.vikrant.careSync.service.ai.GrokClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
        String warningMessage = "⚠️ **URGENT MEDICAL NOTICE**: Your message indicates a potential medical emergency.\n\n"
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
}
