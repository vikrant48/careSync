package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiClinicalService {

    private final GroqClient groqClient;
    private final GeminiClient geminiClient;
    private final AppointmentRepository appointmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicalSummaryResponse summarizePatientHistory(Long patientId) {
        try {
            List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
            List<MedicalHistory> histories = medicalHistoryRepository.findByPatientId(patientId);

            if (appointments.isEmpty() && histories.isEmpty()) {
                return MedicalSummaryResponse.builder().summary("No medical history found.").success(true).build();
            }

            StringBuilder historyData = new StringBuilder("Patient History:\n\n");
            for (Appointment appt : appointments) {
                String reason = appt.getReason() != null ? appt.getReason() : "";
                historyData.append(String.format("- Date: %s, Reason: %s, Status: %s\n",
                        appt.getAppointmentDateTime(), reason, appt.getStatus()));
            }

            for (MedicalHistory history : histories) {
                String symptoms = history.getSymptoms() != null ? history.getSymptoms() : "";
                String diagnosis = history.getDiagnosis() != null ? history.getDiagnosis() : "";

                historyData.append(String.format("- Date: %s, Symptoms: %s, Diagnosis: %s\n",
                        history.getVisitDate(), symptoms, diagnosis));
            }

            String fullHistoryStr = historyData.toString();
            String systemPrompt = "You are CareSync Medical Assistant. Provide a clean, structured medical summary for clinicians.\n"
                    + "Structure your response clearly with headings and bullet points:\n"
                    + "## Medical History Overview\n"
                    + "[Brief 1-2 sentence overview]\n\n"
                    + "### Timeline of Key Events\n"
                    + "* **[Date]:** [Event description and diagnosis]\n\n"
                    + "### 🩺 Chronic Conditions & Notes\n"
                    + "[Documented chronic conditions or state 'No chronic conditions documented.']";
            String userPrompt = systemPrompt + "\n\nSummarize the following patient's medical history:\n\n"
                    + fullHistoryStr;

            String summary = null;

            // 1. Try Groq API FIRST (Primary AI)
            if (groqClient != null && groqClient.isConfigured()) {
                try {
                    log.info("Generating patient history summary using Groq AI (Primary)...");
                    String promptText = fullHistoryStr.length() > 3000
                            ? fullHistoryStr.substring(0, 3000) + "\n...[Truncated for AI processing]"
                            : fullHistoryStr;
                    summary = groqClient.callGroq(systemPrompt, "Summarize:\n" + promptText, false);
                } catch (Exception groqEx) {
                    log.warn("Groq summarization failed ({}), falling back to Gemini AI...", groqEx.getMessage());
                }
            }

            // 2. Fallback to Gemini API if Groq fails or is not configured
            if ((summary == null || summary.isBlank()) && geminiClient != null && geminiClient.isConfigured()) {
                try {
                    log.info("Generating patient history summary using Google Gemini AI (Fallback)...");
                    summary = geminiClient.generateContent(userPrompt);
                } catch (Exception geminiEx) {
                    log.error("Gemini fallback summarization also failed: {}", geminiEx.getMessage());
                }
            }

            return MedicalSummaryResponse.builder().summary(summary).success(true).build();

        } catch (Exception e) {
            log.error("Error summarizing patient history", e);
            return MedicalSummaryResponse.builder().success(false).error("Clinical service error: " + e.getMessage())
                    .build();
        }
    }

    public DiagnosisSuggestionDto suggestDiagnosis(String symptoms) {
        String systemPrompt = "You are CareSync Clinical Assistant. "
                + "Provide 2-4 preliminary potential differential diagnoses based on reported symptoms. "
                + "IMPORTANT SAFETY GUARDRAILS: All outputs represent possible differential conditions only for professional review, NOT a final medical diagnosis.\n"
                + "Respond STRICTLY in JSON format matching this schema:\n"
                + "{\n"
                + "  \"disclaimer\": \"Possible differential conditions only, NOT a final medical diagnosis. Full clinical examination and diagnostic tests are required.\",\n"
                + "  \"suggestions\": [\n"
                + "    {\n"
                + "      \"diagnosis\": \"Possible Condition Name\",\n"
                + "      \"treatment\": \"Initial clinical management / evaluation recommendation\",\n"
                + "      \"medicine\": \"Potential pharmacological option or N/A\",\n"
                + "      \"dosage\": \"Standard dosage guideline or N/A\",\n"
                + "      \"reasoning\": \"Clinical rationale linking symptoms to condition\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        String userPrompt = "Analyze symptoms: '" + symptoms + "'. Return strictly JSON differential suggestions.";

        String jsonResponse = null;

        // 1. Try Groq API FIRST (Primary AI)
        if (groqClient != null && groqClient.isConfigured()) {
            try {
                log.info("Generating differential diagnosis suggestion using Groq AI (Primary)...");
                jsonResponse = groqClient.callGroq(systemPrompt, userPrompt, true);
            } catch (Exception groqEx) {
                log.warn("Groq suggestDiagnosis failed ({}), falling back to Gemini AI...", groqEx.getMessage());
            }
        }

        // 2. Fallback to Gemini API if Groq fails or returns empty
        if ((jsonResponse == null || jsonResponse.isBlank()) && geminiClient != null && geminiClient.isConfigured()) {
            try {
                log.info("Generating differential diagnosis suggestion using Gemini AI (Fallback)...");
                String geminiPrompt = systemPrompt + "\n\n" + userPrompt;
                jsonResponse = geminiClient.generateContent(geminiPrompt);
            } catch (Exception geminiEx) {
                log.error("Gemini suggestDiagnosis fallback failed: {}", geminiEx.getMessage());
            }
        }

        try {
            if (jsonResponse != null && !jsonResponse.isBlank()) {
                String cleanJson = extractJson(jsonResponse);
                DiagnosisSuggestionDto dto = objectMapper.readValue(cleanJson, DiagnosisSuggestionDto.class);
                if (dto.getDisclaimer() == null || dto.getDisclaimer().isBlank()) {
                    dto.setDisclaimer(
                            "Possible conditions only, not a final medical diagnosis. Clinical examination required.");
                }
                return dto;
            }
        } catch (Exception e) {
            log.error("Error parsing diagnosis suggestion JSON: {}", e.getMessage());
        }

        return DiagnosisSuggestionDto.builder()
                .disclaimer("Possible conditions only, not a final medical diagnosis. Clinical examination required.")
                .build();
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
}
