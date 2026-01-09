package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AppointmentRepository appointmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentService appointmentService;
    private final DoctorLeaveService doctorLeaveService;

    public AiChatResponse getResponse(AiChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return AiChatResponse.builder()
                    .success(false)
                    .error("Gemini API key is not configured.")
                    .build();
        }

        try {
            String userMessage = request.getMessage();

            // Handle UI-triggered selections
            if (userMessage.equals("ACTION_GET_SPECIALIZATIONS")) {
                return handleGetSpecializations();
            } else if (userMessage.startsWith("ACTION_SELECT_SPECIALIZATION_")) {
                return handleSpecializationSelection(userMessage);
            } else if (userMessage.startsWith("ACTION_SELECT_DOCTOR_")) {
                return handleDoctorSelection(userMessage);
            } else if (userMessage.startsWith("ACTION_SELECT_DATE_")) {
                return handleDateSelection(userMessage);
            } else if (userMessage.startsWith("ACTION_SELECT_SLOT_")) {
                return handleSlotSelection(userMessage);
            }

            String lowerMsg = userMessage.toLowerCase();

            // 1. Check for specialization mentions (e.g., "select cardiology" or just
            // "cardiology")
            List<String> specializations = getAvailableSpecializations();
            for (String spec : specializations) {
                if (lowerMsg.contains(spec.toLowerCase())) {
                    return handleSpecializationSelection("ACTION_SELECT_SPECIALIZATION_" + spec);
                }
            }

            // 2. Check for doctor names (e.g., "see dr vikrant" or "book vikrant")
            List<Doctor> doctors = doctorRepository.findAll();
            for (Doctor d : doctors) {
                String fullName = (d.getName() != null ? d.getName() : "").toLowerCase();
                String lastName = (d.getLastName() != null ? d.getLastName() : "").toLowerCase();
                if (!fullName.isEmpty() && lowerMsg.contains(fullName)) {
                    return handleDoctorSelection("ACTION_SELECT_DOCTOR_" + d.getId());
                } else if (!lastName.isEmpty() && lowerMsg.contains(lastName)) {
                    return handleDoctorSelection("ACTION_SELECT_DOCTOR_" + d.getId());
                }
            }

            // Detection for generic booking intent
            if (lowerMsg.contains("book") || lowerMsg.contains("appointment") || lowerMsg.contains("see a doctor")
                    || lowerMsg.contains("doctor")) {
                return handleGetSpecializations();
            }

            String specializationsList = String.join(", ", getAvailableSpecializations());
            String systemInstructions = "You are CareSync AI, a professional health assistant. " +
                    "Your goal is to help patients with health questions and booking appointments. " +
                    "If a user describes symptoms, analyze them and identify the most relevant specialization from this list: ["
                    + specializationsList + "]. " +
                    "If you identify a specialization, append this tag to the END of your response (do not show it to the user): RECOMMENDED_SPECIALIZATION: [Name]. "
                    +
                    "ALWAYS include a medical disclaimer. " +
                    "Be empathetic and concise.";

            String prompt = systemInstructions + "\n\nUser Question: " + userMessage;
            AiChatResponse aiResponse = callGemini(prompt);

            // Handle Symptom Routing Confirmation
            if (aiResponse.isSuccess() && aiResponse.getResponse().contains("RECOMMENDED_SPECIALIZATION:")) {
                String fullResponse = aiResponse.getResponse();
                int tagIndex = fullResponse.indexOf("RECOMMENDED_SPECIALIZATION:");
                String spec = fullResponse.substring(tagIndex + "RECOMMENDED_SPECIALIZATION:".length()).trim()
                        .replace("[", "").replace("]", "").replace(".", "");
                String cleanResponse = fullResponse.substring(0, tagIndex).trim();

                // Ensure the extracted spec is valid
                if (getAvailableSpecializations().contains(spec)) {
                    return AiChatResponse.builder()
                            .response(cleanResponse + "\n\nWould you like to see our " + spec + " specialists?")
                            .success(true)
                            .suggestion(AiBookingSuggestion.builder()
                                    .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                                    .specializations(List.of(spec))
                                    .build())
                            .build();
                }

                // Fallback to clean response if spec is invalid
                return AiChatResponse.builder()
                        .response(cleanResponse)
                        .success(true)
                        .build();
            }

            return aiResponse;

        } catch (Exception e) {
            log.error("Error communicating with Gemini API", e);
            return AiChatResponse.builder()
                    .success(false)
                    .error("Internal error: " + e.getMessage())
                    .build();
        }
    }

    private AiChatResponse handleGetSpecializations() {
        List<String> specializations = getAvailableSpecializations();

        return AiChatResponse.builder()
                .response("Please select a specialization to see our available doctors:")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                        .specializations(specializations)
                        .build())
                .build();
    }

    private List<String> getAvailableSpecializations() {
        return doctorRepository.findAll().stream()
                .map(Doctor::getSpecialization)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private AiChatResponse handleSpecializationSelection(String message) {
        String spec = message.replace("ACTION_SELECT_SPECIALIZATION_", "");
        List<Doctor> doctors = doctorRepository.findAll().stream()
                .filter(d -> spec.equalsIgnoreCase(d.getSpecialization()))
                .collect(Collectors.toList());

        List<AiBookingSuggestion.DoctorSuggestion> suggestions = doctors.stream()
                .map(d -> {
                    int totalExp = d.getExperiences() != null ? d.getExperiences().stream()
                            .mapToInt(com.vikrant.careSync.entity.Experience::getYearsOfService).sum() : 0;

                    boolean onLeave = doctorLeaveService.isDoctorOnLeave(d.getId(), LocalDate.now());

                    return AiBookingSuggestion.DoctorSuggestion.builder()
                            .id(d.getId())
                            .name(d.getName())
                            .specialization(d.getSpecialization())
                            .consultationFee(d.getConsultationFees())
                            .profileImageUrl(d.getProfileImageUrl())
                            .languages(d.getLanguages())
                            .experience(totalExp)
                            .isOnLeave(onLeave)
                            .leaveMessage(onLeave ? "Currently Away" : null)
                            .build();
                })
                .collect(Collectors.toList());

        return AiChatResponse.builder()
                .response("Here are our " + spec + " specialists. Please select one to proceed:")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                        .doctors(suggestions)
                        .build())
                .build();
    }

    private AiChatResponse handleDoctorSelection(String message) {
        Long doctorId = Long.parseLong(message.replace("ACTION_SELECT_DOCTOR_", ""));
        Doctor d = doctorRepository.findById(doctorId).orElseThrow();

        return AiChatResponse.builder()
                .response("When would you like to see " + d.getName() + "?")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DATES)
                        .doctorId(d.getId())
                        .doctorName(d.getName())
                        .build())
                .build();
    }

    private AiChatResponse handleDateSelection(String message) {
        // Format: ACTION_SELECT_DATE_{doctorId}_{date}
        String[] parts = message.replace("ACTION_SELECT_DATE_", "").split("_");
        Long doctorId = Long.parseLong(parts[0]);
        String date = parts[1]; // YYYY-MM-DD

        Doctor d = doctorRepository.findById(doctorId).orElseThrow();
        List<String> slots = appointmentService.getAvailableSlots(doctorId, date);

        return AiChatResponse.builder()
                .response("Available slots for " + d.getName() + " on " + date + ":")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.SLOTS)
                        .doctorId(d.getId())
                        .doctorName(d.getName())
                        .date(date)
                        .slots(slots)
                        .build())
                .build();
    }

    private AiChatResponse handleSlotSelection(String message) {
        // Format: ACTION_SELECT_SLOT_{doctorId}_{date}_{slot}
        String[] parts = message.replace("ACTION_SELECT_SLOT_", "").split("_");
        Long doctorId = Long.parseLong(parts[0]);
        String date = parts[1];
        String slot = parts[2];

        Doctor d = doctorRepository.findById(doctorId).orElseThrow();

        return AiChatResponse.builder()
                .response(
                        "Confirm booking with " + d.getName() + " on " + date + " at " + slot + ". Consulting fee is ₹"
                                + d.getConsultationFees() + ".")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.CONFIRM)
                        .doctorId(d.getId())
                        .doctorName(d.getName())
                        .date(date)
                        .slot(slot)
                        .consultationFee(d.getConsultationFees())
                        .build())
                .build();
    }

    public MedicalSummaryResponse summarizePatientHistory(Long patientId) {
        if (apiKey == null || apiKey.isBlank()) {
            return MedicalSummaryResponse.builder()
                    .success(false)
                    .error("Gemini API key is not configured.")
                    .build();
        }

        try {
            List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
            List<MedicalHistory> histories = medicalHistoryRepository.findByPatientId(patientId);

            if (appointments.isEmpty() && histories.isEmpty()) {
                return MedicalSummaryResponse.builder()
                        .summary("No medical history found for this patient.")
                        .success(true)
                        .build();
            }

            StringBuilder historyData = new StringBuilder();
            historyData.append("Patient History for Summary:\n\n");

            if (!appointments.isEmpty()) {
                historyData.append("--- Past Appointments ---\n");
                for (Appointment appt : appointments) {
                    historyData.append(String.format("- Date: %s, Reason: %s, Status: %s\n",
                            appt.getAppointmentDateTime(), appt.getReason(), appt.getStatus()));
                }
            }

            if (!histories.isEmpty()) {
                historyData.append("\n--- Clinical Notes & Records ---\n");
                for (MedicalHistory history : histories) {
                    historyData.append(String.format(
                            "- Date: %s\n  Symptoms: %s\n  Diagnosis: %s\n  Treatment: %s\n  Medicine: %s\n  Notes: %s\n",
                            history.getVisitDate(), history.getSymptoms(), history.getDiagnosis(),
                            history.getTreatment(), history.getMedicine(), history.getNotes()));
                }
            }

            String prompt = "You are a professional medical scribe. Summarize the following patient's medical history for a doctor in a concise, structured way. "
                    +
                    "Focus on recurring symptoms, significant diagnoses, treatments, and ongoing concerns. " +
                    "Format the output using clear headings and bullet points.\n\n" + historyData.toString();

            AiChatResponse aiResponse = callGemini(prompt);

            if (aiResponse.isSuccess()) {
                return MedicalSummaryResponse.builder()
                        .summary(aiResponse.getResponse())
                        .success(true)
                        .build();
            } else {
                return MedicalSummaryResponse.builder()
                        .success(false)
                        .error(aiResponse.getError())
                        .build();
            }

        } catch (Exception e) {
            log.error("Error generating medical summary", e);
            return MedicalSummaryResponse.builder()
                    .success(false)
                    .error("Internal error: " + e.getMessage())
                    .build();
        }
    }

    private AiChatResponse callGemini(String prompt) {
        GeminiRequest geminiRequest = GeminiRequest.fromText(prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String fullUrl = apiUrl + "?key=" + apiKey;
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(geminiRequest, headers);

        GeminiResponse geminiResponse = restTemplate.postForObject(fullUrl, entity, GeminiResponse.class);

        if (geminiResponse != null && geminiResponse.getFirstText() != null) {
            return AiChatResponse.builder()
                    .response(geminiResponse.getFirstText())
                    .success(true)
                    .build();
        } else {
            return AiChatResponse.builder()
                    .success(false)
                    .error("Could not get a valid response from AI service.")
                    .build();
        }
    }
}
