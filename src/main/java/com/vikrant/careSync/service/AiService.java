package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.master.SpecializationMasterRepository;
import com.vikrant.careSync.entity.master.SpecializationMaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;
    private final DoctorLeaveService doctorLeaveService;
    private final FeedbackService feedbackService;
    private final LabTestService labTestService;
    private final SpecializationMasterRepository specializationMasterRepository;

    private static final Long DEFAULT_ORG_ID = 91L;

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
            } else if (userMessage.startsWith("ACTION_CANCEL_APPOINTMENT_")) {
                return handleCancelAppointment(userMessage);
            } else if (userMessage.startsWith("ACTION_START_RESCHEDULE_")) {
                return handleStartReschedule(userMessage);
            }

            String lowerMsg = userMessage.toLowerCase();

            // 1. Check for specialization mentions
            List<String> specializations = getAvailableSpecializations();
            for (String spec : specializations) {
                if (lowerMsg.contains(spec.toLowerCase())) {
                    return handleSpecializationSelection("ACTION_SELECT_SPECIALIZATION_" + spec);
                }
            }

            // 2. Check for doctor names
            List<Doctor> allDoctors = doctorRepository.findAll();
            for (Doctor d : allDoctors) {
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
                if (!lowerMsg.contains("cancel") && !lowerMsg.contains("reschedule") && !lowerMsg.contains("move")) {
                    return handleGetSpecializations();
                }
            }

            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isDoctor = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));

            String specializationsList = String.join(", ", getAvailableSpecializations());
            String systemInstructions;

            if (isDoctor) {
                systemInstructions = "You are CareSync AI Clinical Assistant, a specialized advisor for medical professionals. "
                        +
                        "Your goal is to provide evidence-based clinical information, drug interactions, and patient data summaries. "
                        +
                        "- If asked about medications: provide common interactions, side effects, and standard dosages. "
                        +
                        "- If asked to summarize a patient: focus on key clinical events, chronic conditions, and recent symptoms. "
                        +
                        "- Always maintain a professional, clinical tone. " +
                        "- Include a medical disclaimer that yours is an assistive tool and not a substitute for clinical judgment.";
            } else {
                systemInstructions = "You are CareSync AI, a professional health assistant. " +
                        "Your goal is to help patients with health questions, booking, canceling, and rescheduling appointments. "
                        +
                        "- If a user describes symptoms: identify relevant specialization(s) from ["
                        + specializationsList + "] and append RECOMMENDED_SPECIALIZATIONS: [Spec1, ...]. " +
                        "- If a user wants to cancel: append RECOMMENDED_ACTION: CANCEL. " +
                        "- If a user wants to move/reschedule: append RECOMMENDED_ACTION: RESCHEDULE. " +
                        "In your user-facing response, explicitly mention which specialists they should see or ask for confirmation about cancel/reschedule. "
                        +
                        "ALWAYS include a medical disclaimer at the end. " +
                        "Be empathetic, professional, and concise.";
            }

            // For doctors, we might want to automatically include some patient list context
            // if they ask "John Doe"
            String context = "";
            if (isDoctor && (userMessage.toLowerCase().contains("summarize")
                    || userMessage.toLowerCase().contains("history"))) {
                // Simplified: search for patient names in message
                List<Patient> patients = patientRepository.findAll();
                for (Patient p : patients) {
                    if (userMessage.toLowerCase().contains(p.getName().toLowerCase())) {
                        MedicalSummaryResponse summary = summarizePatientHistory(p.getId());
                        if (summary.isSuccess()) {
                            context = "\n[CONTEXT] Patient " + p.getName() + " History Summary: " + summary.getSummary()
                                    + "\n";
                            break;
                        }
                    }
                }
            }

            String prompt = systemInstructions + context + "\n\nUser Question: " + userMessage;
            AiChatResponse aiResponse = callGemini(prompt);

            if (aiResponse.isSuccess()) {
                String responseText = aiResponse.getResponse();

                // Handle Symptom Routing
                if (responseText.contains("RECOMMENDED_SPECIALIZATIONS:")) {
                    return handleRecommendedSpecializations(responseText, request.getMessage());
                }

                // Handle CANCEL Action
                if (responseText.contains("RECOMMENDED_ACTION: CANCEL")) {
                    return handleFetchMyAppointments("Which appointment would you like to cancel?",
                            responseText.replace("RECOMMENDED_ACTION: CANCEL", "").trim());
                }

                // Handle RESCHEDULE Action
                if (responseText.contains("RECOMMENDED_ACTION: RESCHEDULE")) {
                    return handleFetchMyAppointments("Which appointment would you like to move?",
                            responseText.replace("RECOMMENDED_ACTION: RESCHEDULE", "").trim());
                }
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

    private AiChatResponse handleRecommendedSpecializations(String responseText, String originalUserMsg) {
        int tagIndex = responseText.indexOf("RECOMMENDED_SPECIALIZATIONS:");
        String specsPart = responseText.substring(tagIndex + "RECOMMENDED_SPECIALIZATIONS:".length()).trim()
                .replace("[", "").replace("]", "").replace(".", "");
        String cleanResponse = responseText.substring(0, tagIndex).trim();

        // Use user message as initial reason (truncated if too long)
        String initialReason = originalUserMsg != null
                ? (originalUserMsg.length() > 250 ? originalUserMsg.substring(0, 247) + "..." : originalUserMsg)
                : "AI Assisted Booking";

        List<String> specializationsList = getAvailableSpecializations();
        List<String> recommendedSpecs = java.util.Arrays.stream(specsPart.split(","))
                .map(String::trim)
                .filter(s -> specializationsList.stream().anyMatch(valid -> valid.equalsIgnoreCase(s)))
                .collect(Collectors.toList());

        if (!recommendedSpecs.isEmpty()) {
            List<Doctor> doctors = doctorRepository.findAll().stream()
                    .filter(d -> recommendedSpecs.stream()
                            .anyMatch(spec -> spec.equalsIgnoreCase(d.getSpecialization())))
                    .collect(Collectors.toList());

            if (!doctors.isEmpty()) {
                return AiChatResponse.builder()
                        .response(cleanResponse)
                        .success(true)
                        .suggestion(AiBookingSuggestion.builder()
                                .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                                .doctors(doctors.stream().map(this::mapToDoctorSuggestion).collect(Collectors.toList()))
                                .reason(initialReason)
                                .build())
                        .build();
            } else {
                // Return specializations suggestion if doctors aren't found for the recommended
                // ones
                return AiChatResponse.builder()
                        .response(cleanResponse + "\n\nYou can consult with these specialists:")
                        .success(true)
                        .suggestion(AiBookingSuggestion.builder()
                                .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                                .specializations(recommendedSpecs)
                                .reason(initialReason)
                                .build())
                        .build();
            }
        }

        return AiChatResponse.builder().response(cleanResponse).success(true).build();
    }

    private AiChatResponse handleFetchMyAppointments(String question, String cleanAiResponse) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient == null) {
            return AiChatResponse.builder().response("Please log in as a patient to manage appointments.").success(true)
                    .build();
        }

        List<Appointment> appointments = appointmentService.getUpcomingPatientAppointments(patient.getId());
        if (appointments.isEmpty()) {
            return AiChatResponse.builder().response("You don't have any upcoming appointments.").success(true).build();
        }

        List<AppointmentDto> appointmentDtos = appointments.stream()
                .map(AppointmentDto::new)
                .collect(Collectors.toList());

        return AiChatResponse.builder()
                .response(cleanAiResponse + "\n\n" + question)
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.MY_APPOINTMENTS)
                        .appointments(appointmentDtos)
                        .build())
                .build();
    }

    private AiChatResponse handleCancelAppointment(String message) {
        Long apptId = Long.parseLong(message.replace("ACTION_CANCEL_APPOINTMENT_", ""));
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = patientRepository.findByUsername(username).map(p -> (User) p).orElse(null);

        try {
            appointmentService.cancelAppointment(apptId, currentUser);
            return AiChatResponse.builder().response("Appointment canceled successfully.").success(true).build();
        } catch (Exception e) {
            return AiChatResponse.builder().response("Error canceling appointment: " + e.getMessage()).success(true)
                    .build();
        }
    }

    private AiChatResponse handleStartReschedule(String message) {
        Long apptId = Long.parseLong(message.replace("ACTION_START_RESCHEDULE_", ""));
        Appointment appt = appointmentRepository.findById(apptId).orElseThrow();

        return AiChatResponse.builder()
                .response("When would you like to move your appointment with " + appt.getDoctor().getName() + "?")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DATES)
                        .doctorId(appt.getDoctor().getId())
                        .doctorName(appt.getDoctor().getName())
                        .appointmentId(apptId)
                        .originalDate(appt.getAppointmentDateTime().toLocalDate().toString())
                        .originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5))
                        .reason("RESCHEDULE:" + apptId)
                        .build())
                .build();
    }

    private AiChatResponse handleGetSpecializations() {
        List<String> specializations = getAvailableSpecializations();
        return AiChatResponse.builder()
                .response("Please select a specialization:")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                        .specializations(specializations)
                        .build())
                .build();
    }

    private List<String> getAvailableSpecializations() {
        Set<String> allSpecs = specializationMasterRepository.findByOrgId(DEFAULT_ORG_ID)
                .stream().map(SpecializationMaster::getValue).collect(Collectors.toSet());

        doctorRepository.findAll().stream()
                .map(Doctor::getSpecialization)
                .filter(s -> s != null && !s.isBlank())
                .forEach(allSpecs::add);

        return allSpecs.stream().sorted().collect(Collectors.toList());
    }

    private AiBookingSuggestion.DoctorSuggestion mapToDoctorSuggestion(Doctor d) {
        int totalExp = d.getExperiences() != null ? d.getExperiences().stream()
                .mapToInt(com.vikrant.careSync.entity.Experience::getYearsOfService).sum() : 0;
        boolean onLeave = doctorLeaveService.isDoctorOnLeave(d.getId(), LocalDate.now());
        return AiBookingSuggestion.DoctorSuggestion.builder()
                .id(d.getId()).name(d.getName()).specialization(d.getSpecialization())
                .consultationFee(d.getConsultationFees()).profileImageUrl(d.getProfileImageUrl())
                .languages(d.getLanguages()).experience(totalExp).isOnLeave(onLeave)
                .leaveMessage(onLeave ? "Away" : null)
                .isVerified(d.getIsVerified() != null && d.getIsVerified())
                .build();
    }

    private AiChatResponse handleSpecializationSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_SPECIALIZATION_", "").split("_", 2);
        String spec = parts[0];
        String reason = parts.length > 1 ? parts[1] : "AI Assisted Booking";

        List<Doctor> doctors = doctorRepository.findAll().stream()
                .filter(d -> spec.equalsIgnoreCase(d.getSpecialization())).collect(Collectors.toList());
        List<AiBookingSuggestion.DoctorSuggestion> suggestions = doctors.stream()
                .map(this::mapToDoctorSuggestion).collect(Collectors.toList());
        return AiChatResponse.builder()
                .response("Here are our " + spec + " specialists:")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                        .doctors(suggestions)
                        .reason(reason).build())
                .build();
    }

    private AiChatResponse handleDoctorSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_DOCTOR_", "").split("_", 2);
        Long doctorId = Long.parseLong(parts[0]);
        String reason = parts.length > 1 ? parts[1] : "AI Assisted Booking";

        Doctor d = doctorRepository.findById(doctorId).orElseThrow();
        return AiChatResponse.builder()
                .response("When would you like to see " + d.getName() + "?")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DATES)
                        .doctorId(d.getId()).doctorName(d.getName())
                        .reason(reason).build())
                .build();
    }

    private AiChatResponse handleDateSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_DATE_", "").split("_", 3);
        Long doctorId = Long.parseLong(parts[0]);
        String date = parts[1];
        String reason = parts.length > 2 ? parts[2] : "AI Assisted Booking";

        Doctor d = doctorRepository.findById(doctorId).orElseThrow();
        com.vikrant.careSync.dto.SlotAvailabilityResponse slotResponse = appointmentService.getAvailableSlots(doctorId,
                date);
        if (slotResponse.isOnLeave()) {
            return AiChatResponse.builder()
                    .response(slotResponse.getLeaveMessage())
                    .success(true)
                    .build();
        }

        List<String> slots = slotResponse.getAvailableSlots();

        AiBookingSuggestion.AiBookingSuggestionBuilder suggestionBuilder = AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.SLOTS)
                .doctorId(d.getId()).doctorName(d.getName()).date(date)
                .slots(slots).reason(reason);

        if (reason.startsWith("RESCHEDULE:")) {
            Long apptId = Long.parseLong(reason.split(":")[1]);
            suggestionBuilder.appointmentId(apptId);
            appointmentRepository.findById(apptId).ifPresent(appt -> {
                suggestionBuilder.originalDate(appt.getAppointmentDateTime().toLocalDate().toString());
                suggestionBuilder.originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5));
            });
        }

        return AiChatResponse.builder()
                .response("Available slots for " + d.getName() + " on " + date + ":")
                .success(true)
                .suggestion(suggestionBuilder.build())
                .build();
    }

    private AiChatResponse handleSlotSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_SLOT_", "").split("_", 4);
        Long doctorId = Long.parseLong(parts[0]);
        String date = parts[1];
        String slot = parts[2];
        String reason = parts.length > 3 ? parts[3] : "AI Assisted Booking";

        Doctor d = doctorRepository.findById(doctorId).orElseThrow();

        AiBookingSuggestion.AiBookingSuggestionBuilder suggestionBuilder = AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.CONFIRM)
                .doctorId(d.getId()).doctorName(d.getName()).date(date)
                .slot(slot).consultationFee(d.getConsultationFees()).reason(reason);

        if (reason.startsWith("RESCHEDULE:")) {
            Long apptId = Long.parseLong(reason.split(":")[1]);
            suggestionBuilder.appointmentId(apptId);
            appointmentRepository.findById(apptId).ifPresent(appt -> {
                suggestionBuilder.originalDate(appt.getAppointmentDateTime().toLocalDate().toString());
                suggestionBuilder.originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5));
            });
        }

        return AiChatResponse.builder()
                .response("Confirm " + (reason.startsWith("RESCHEDULE") ? "rescheduling" : "booking") + " with "
                        + d.getName() + " on " + date + " at " + slot + ".")
                .success(true)
                .suggestion(suggestionBuilder.build())
                .build();
    }

    public MedicalSummaryResponse summarizePatientHistory(Long patientId) {
        if (apiKey == null || apiKey.isBlank()) {
            return MedicalSummaryResponse.builder().success(false).error("Gemini API key is not configured.").build();
        }

        try {
            List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
            List<MedicalHistory> histories = medicalHistoryRepository.findByPatientId(patientId);

            if (appointments.isEmpty() && histories.isEmpty()) {
                return MedicalSummaryResponse.builder().summary("No medical history found.").success(true).build();
            }

            StringBuilder historyData = new StringBuilder("Patient History:\n\n");
            for (Appointment appt : appointments) {
                historyData.append(String.format("- Date: %s, Reason: %s, Status: %s\n", appt.getAppointmentDateTime(),
                        appt.getReason(), appt.getStatus()));
            }
            for (MedicalHistory history : histories) {
                historyData.append(String.format("- Date: %s, Symptoms: %s, Diagnosis: %s\n", history.getVisitDate(),
                        history.getSymptoms(), history.getDiagnosis()));
            }

            String prompt = "Summarize the following patient's medical history concisely:\n\n" + historyData.toString();
            AiChatResponse aiResponse = callGemini(prompt);

            if (aiResponse.isSuccess()) {
                return MedicalSummaryResponse.builder().summary(aiResponse.getResponse()).success(true).build();
            } else {
                return MedicalSummaryResponse.builder().success(false).error(aiResponse.getError()).build();
            }
        } catch (Exception e) {
            return MedicalSummaryResponse.builder().success(false).error("Internal error: " + e.getMessage()).build();
        }
    }

    public DiagnosisSuggestionDto suggestDiagnosis(String symptoms) {
        if (apiKey == null || apiKey.isBlank()) {
            return DiagnosisSuggestionDto.builder().build();
        }

        String prompt = "As a medical clinical assistant, analyze these symptoms: '" + symptoms + "'. " +
                "Suggest 3 possible diagnoses and corresponding treatment plans. " +
                "Return the response in STRICT JSON format with this structure: " +
                "{\"suggestions\": [{\"diagnosis\": \"...\", \"treatment\": \"...\", \"medicine\": \"...\", \"dosage\": \"...\", \"reasoning\": \"...\"}]}. "
                +
                "Only return the JSON object, nothing else. Ensure the suggestions are diverse if symptoms are broad.";

        try {
            AiChatResponse aiResponse = callGemini(prompt);
            if (aiResponse.isSuccess() && aiResponse.getResponse() != null) {
                String cleanJson = extractJson(aiResponse.getResponse());
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cleanJson,
                        DiagnosisSuggestionDto.class);
            }
        } catch (Exception e) {
            log.error("Error suggesting diagnosis", e);
        }
        return DiagnosisSuggestionDto.builder().build();
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

    private AiChatResponse callGemini(String prompt) {
        GeminiRequest geminiRequest = GeminiRequest.fromText(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String fullUrl = apiUrl + "?key=" + apiKey;
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(geminiRequest, headers);

        int maxRetries = 3;
        int retryDelay = 1000; // 1 second base delay

        for (int i = 0; i < maxRetries; i++) {
            try {
                GeminiResponse geminiResponse = restTemplate.postForObject(fullUrl, entity, GeminiResponse.class);
                if (geminiResponse != null && geminiResponse.getFirstText() != null) {
                    return AiChatResponse.builder().response(geminiResponse.getFirstText()).success(true).build();
                }
            } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
                log.warn("Gemini API overloaded (503). Retrying {}/{}...", i + 1, maxRetries);
                if (i == maxRetries - 1)
                    break;
                try {
                    Thread.sleep(retryDelay * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("Gemini API call failed", e);
                break;
            }
        }
        return AiChatResponse.builder()
                .success(false)
                .error("AI service is currently busy. Please try again in a moment.")
                .build();
    }
}
