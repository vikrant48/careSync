package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.AppointmentDto;
import com.vikrant.careSync.dto.SlotAvailabilityResponse;
import com.vikrant.careSync.entity.*;
import com.vikrant.careSync.entity.master.SpecializationMaster;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.master.SpecializationMasterRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.DoctorLeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiBookingService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final DoctorLeaveService doctorLeaveService;
    private final SpecializationMasterRepository specializationMasterRepository;
    private final AccessGuard accessGuard;

    private static final Long DEFAULT_ORG_ID = 91L;

    public List<String> getAvailableSpecializations() {
        Set<String> allSpecs = specializationMasterRepository.findByOrgId(DEFAULT_ORG_ID)
                .stream().map(SpecializationMaster::getValue).collect(Collectors.toSet());

        doctorRepository.findDistinctSpecializations().stream()
                .filter(s -> s != null && !s.isBlank())
                .forEach(allSpecs::add);

        return allSpecs.stream().sorted().collect(Collectors.toList());
    }

    public AiChatResponse handleGetSpecializations() {
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

    public AiChatResponse handleSpecializationSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_SPECIALIZATION_", "").split("_", 2);
        String spec = parts[0];
        String reason = parts.length > 1 ? parts[1] : "AI Assisted Booking";

        List<Doctor> doctors = doctorRepository.findBookableBySpecialization(spec).stream()
                .limit(3)
                .collect(Collectors.toList());
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

    public AiChatResponse handleDoctorSelection(String message) {
        try {
            validatePatientAuthenticated();
            String[] parts = message.replace("ACTION_SELECT_DOCTOR_", "").split("_", 2);
            Long doctorId = Long.parseLong(parts[0]);
            String reason = parts.length > 1 ? parts[1] : "AI Assisted Booking";

            Doctor d = validateDoctorExists(doctorId);
            return AiChatResponse.builder()
                    .response("When would you like to see " + d.getName() + "?")
                    .success(true)
                    .suggestion(AiBookingSuggestion.builder()
                            .type(AiBookingSuggestion.SuggestionType.DATES)
                            .doctorId(d.getId()).doctorName(d.getName())
                            .specialization(d.getSpecialization())
                            .reason(reason).build())
                    .build();
        } catch (Exception e) {
            log.warn("Safety validation failed during doctor selection: {}", e.getMessage());
            return AiChatResponse.builder().response("⚠️ " + e.getMessage()).success(false).build();
        }
    }

    public AiChatResponse handleDateSelection(String message) {
        try {
            validatePatientAuthenticated();
            String[] parts = message.replace("ACTION_SELECT_DATE_", "").split("_", 3);
            Long doctorId = Long.parseLong(parts[0]);
            String date = parts[1];
            String reason = parts.length > 2 ? parts[2] : "AI Assisted Booking";

            Doctor d = validateDoctorExists(doctorId);
            LocalDate bookingDate = LocalDate.parse(date);
            validateDoctorNotOnLeave(doctorId, bookingDate);

            SlotAvailabilityResponse slotResponse = appointmentService.getAvailableSlots(doctorId, date);
            if (slotResponse.isOnLeave()) {
                return AiChatResponse.builder()
                        .response(slotResponse.getLeaveMessage())
                        .success(false)
                        .build();
            }

            List<String> slots = slotResponse.getAvailableSlots();

            AiBookingSuggestion.AiBookingSuggestionBuilder suggestionBuilder = AiBookingSuggestion.builder()
                    .type(AiBookingSuggestion.SuggestionType.SLOTS)
                    .doctorId(d.getId()).doctorName(d.getName()).date(date)
                    .slots(slots).reason(reason);

            if (reason.startsWith("RESCHEDULE:")) {
                Long apptId = Long.parseLong(reason.split(":")[1]);
                requireOwnAppointment(apptId);
                suggestionBuilder.appointmentId(apptId);
                appointmentRepository.findById(apptId).ifPresent(appt -> {
                    suggestionBuilder.originalDate(appt.getAppointmentDateTime().toLocalDate().toString());
                    suggestionBuilder
                            .originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5));
                });
            }

            return AiChatResponse.builder()
                    .response("Available slots for " + d.getName() + " on " + date + ":")
                    .success(true)
                    .suggestion(suggestionBuilder.build())
                    .build();
        } catch (Exception e) {
            log.warn("Safety validation failed during date selection: {}", e.getMessage());
            return AiChatResponse.builder().response("⚠️ " + e.getMessage()).success(false).build();
        }
    }

    public AiChatResponse handleSlotSelection(String message) {
        String[] parts = message.replace("ACTION_SELECT_SLOT_", "").split("_", 4);
        try {
            return prepareConfirmation(Long.parseLong(parts[0]), parts[1], parts[2],
                    parts.length > 3 ? parts[3] : "AI Assisted Booking");
        } catch (Exception e) {
            log.warn("Safety validation failed during slot selection: {}", e.getMessage());
            return AiChatResponse.builder().response("⚠️ " + e.getMessage()).success(false).build();
        }
    }

    /**
     * The confirm card for a booking (or, with reason "RESCHEDULE:{id}", a reschedule), after every safety check:
     * logged-in active patient, doctor accepting appointments, not on leave, slot still free, no duplicate, own
     * appointment for reschedules. Nothing is booked here: the patient confirms (and pays) from the card.
     * Throws with a readable message when a check fails.
     */
    public AiChatResponse prepareConfirmation(Long doctorId, String date, String slot, String reason) {
        Patient patient = validatePatientAuthenticated();
        Doctor d = validateDoctorExists(doctorId);
        LocalDate bookingDate = LocalDate.parse(date);

        // Safety validations
        validateDoctorNotOnLeave(doctorId, bookingDate);
        validateSlotAvailability(doctorId, date, slot);

        if (!reason.startsWith("RESCHEDULE:")) {
            validateNoDuplicateAppointment(patient.getId(), doctorId, bookingDate, slot);
        }

        AiBookingSuggestion.AiBookingSuggestionBuilder suggestionBuilder = AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.CONFIRM)
                .doctorId(d.getId()).doctorName(d.getName()).date(date)
                .specialization(d.getSpecialization())
                .slot(slot).consultationFee(d.getConsultationFees()).reason(reason);

        if (reason.startsWith("RESCHEDULE:")) {
            Long apptId = Long.parseLong(reason.split(":")[1]);
            requireOwnAppointment(apptId);
            suggestionBuilder.appointmentId(apptId);
            appointmentRepository.findById(apptId).ifPresent(appt -> {
                suggestionBuilder.originalDate(appt.getAppointmentDateTime().toLocalDate().toString());
                suggestionBuilder
                        .originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5));
            });
        }

        return AiChatResponse.builder()
                .response("Confirm " + (reason.startsWith("RESCHEDULE") ? "rescheduling" : "booking") + " with "
                        + d.getName() + " on " + date + " at " + slot + ".")
                .success(true)
                .suggestion(suggestionBuilder.build())
                .build();
    }

    /** The logged-in patient (active, allowed to book); throws with a readable message otherwise. */
    public Patient currentPatient() {
        return validatePatientAuthenticated();
    }

    /** A doctor that exists and accepts appointments; throws with a readable message otherwise. */
    public Doctor bookableDoctor(Long doctorId) {
        return validateDoctorExists(doctorId);
    }

    /** True when the logged-in patient owns this appointment */
    public boolean ownsAppointment(Long appointmentId) {
        return accessGuard.isAppointmentPatient(appointmentId);
    }

    /** Doctor card entry with the doctor's first open slot in the next two weeks (no slot when none) */
    public AiBookingSuggestion.DoctorSuggestion doctorWithFirstSlot(Doctor doctor) {
        return mapToDoctorSuggestionWithFirstSlot(doctor);
    }

    // Safety Validation Guardrails
    private void requireOwnAppointment(Long appointmentId) {
        if (!accessGuard.isAppointmentPatient(appointmentId)) {
            throw new IllegalStateException("You can only reschedule your own appointments.");
        }
    }

    private Patient validatePatientAuthenticated() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || "anonymousUser".equalsIgnoreCase(username)) {
            throw new IllegalStateException("Authentication required: Please log in as a patient to proceed.");
        }
        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient == null) {
            throw new IllegalStateException(
                    "Patient profile not found. Please ensure you are logged in with a valid patient account.");
        }
        if (!patient.canBookAppointment()) {
            throw new IllegalStateException("Your patient account is currently inactive or restricted from booking.");
        }
        return patient;
    }

    private Doctor validateDoctorExists(Long doctorId) {
        if (doctorId == null) {
            throw new IllegalArgumentException("Invalid request: Doctor ID is missing.");
        }
        Doctor d = doctorRepository.findById(doctorId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Requested doctor does not exist (ID: " + doctorId + ")."));
        if (!d.canAcceptAppointments()) {
            throw new IllegalStateException("Doctor " + d.getName() + " is currently not accepting new appointments.");
        }
        return d;
    }

    private void validateDoctorNotOnLeave(Long doctorId, LocalDate date) {
        if (doctorLeaveService.isDoctorOnLeave(doctorId, date)) {
            throw new IllegalStateException("Doctor is on leave on " + date + " and unavailable for appointments.");
        }
    }

    private void validateSlotAvailability(Long doctorId, String date, String slot) {
        SlotAvailabilityResponse slotResponse = appointmentService.getAvailableSlots(doctorId, date);
        if (slotResponse.isOnLeave()) {
            throw new IllegalStateException(slotResponse.getLeaveMessage());
        }
        List<String> availableSlots = slotResponse.getAvailableSlots();
        if (availableSlots == null || !availableSlots.contains(slot)) {
            throw new IllegalStateException(
                    "Time slot " + slot + " on " + date + " is no longer available. Please choose another slot.");
        }
    }

    private void validateNoDuplicateAppointment(Long patientId, Long doctorId, LocalDate date, String slot) {
        List<Appointment> upcoming = appointmentService.getUpcomingPatientAppointments(patientId);
        if (upcoming != null) {
            for (Appointment appt : upcoming) {
                if (appt.getDoctor() != null && appt.getDoctor().getId().equals(doctorId)
                        && appt.getStatus() != Appointment.Status.CANCELLED) {
                    LocalDate apptDate = appt.getAppointmentDateTime().toLocalDate();
                    String apptSlot = appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5);
                    if (apptDate.equals(date) && apptSlot.equalsIgnoreCase(slot)) {
                        throw new IllegalStateException(
                                "Duplicate booking detected: You already have an appointment scheduled with Dr. "
                                        + appt.getDoctor().getName() + " on " + date + " at " + slot + ".");
                    }
                }
            }
        }
    }

    public AiChatResponse handleRecommendedSpecializationList(List<String> recommendedSpecs, String reply,
            String originalUserMsg) {
        String cleanResponse = (reply != null && !reply.isBlank()) ? reply
                : "Based on your request, here are recommended doctors:";
        String initialReason = originalUserMsg != null
                ? (originalUserMsg.length() > 250 ? originalUserMsg.substring(0, 247) + "..." : originalUserMsg)
                : "AI Assisted Booking";

        List<String> specializationsList = getAvailableSpecializations();
        List<String> validSpecs = recommendedSpecs != null ? recommendedSpecs.stream()
                .map(String::trim)
                .filter(s -> specializationsList.stream().anyMatch(valid -> valid.equalsIgnoreCase(s)))
                .collect(Collectors.toList()) : List.of();

        if (!validSpecs.isEmpty()) {
            List<Doctor> doctors = validSpecs.stream()
                    .map(String::toLowerCase)
                    .distinct()
                    .flatMap(spec -> doctorRepository.findBookableBySpecialization(spec).stream())
                    .distinct()
                    .collect(Collectors.toList());

            if (!doctors.isEmpty()) {
                List<AiBookingSuggestion.DoctorSuggestion> availableDoctors = new ArrayList<>();
                for (Doctor doctor : doctors) {
                    AiBookingSuggestion.DoctorSuggestion suggestion = mapToDoctorSuggestionWithFirstSlot(doctor);
                    if (suggestion.getRecommendedSlot() != null) {
                        availableDoctors.add(suggestion);
                    }
                    if (availableDoctors.size() == 3) {
                        break;
                    }
                }
                if (availableDoctors.isEmpty()) {
                    return AiChatResponse.builder()
                            .response(cleanResponse
                                    + "\n\nNo matching doctor has an open slot in the next 14 days. Please choose another specialization.")
                            .success(true)
                            .suggestion(AiBookingSuggestion.builder()
                                    .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                                    .specializations(validSpecs)
                                    .reason(initialReason)
                                    .build())
                            .build();
                }
                return AiChatResponse.builder()
                        .response(cleanResponse)
                        .success(true)
                        .suggestion(AiBookingSuggestion.builder()
                                .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                                .doctors(availableDoctors)
                                .reason(initialReason)
                                .build())
                        .build();
            } else {
                return AiChatResponse.builder()
                        .response(cleanResponse + "\n\nYou can consult with these specialists:")
                        .success(true)
                        .suggestion(AiBookingSuggestion.builder()
                                .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS)
                                .specializations(validSpecs)
                                .reason(initialReason)
                                .build())
                        .build();
            }
        }

        return AiChatResponse.builder().response(cleanResponse).success(true).build();
    }

    public AiChatResponse handleFetchMyAppointments(String question, String cleanAiResponse) {
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

    public AiChatResponse handleCancelAppointment(String message) {
        Long apptId = Long.parseLong(message.replace("ACTION_CANCEL_APPOINTMENT_", ""));
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = patientRepository.findByUsername(username).map(Patient::getUser).orElse(null);

        try {
            appointmentService.cancelAppointment(apptId, currentUser);
            return AiChatResponse.builder().response("Appointment canceled successfully.").success(true).build();
        } catch (Exception e) {
            return AiChatResponse.builder().response("Error canceling appointment: " + e.getMessage()).success(true)
                    .build();
        }
    }

    public AiChatResponse handleStartReschedule(String message) {
        Long apptId = Long.parseLong(message.replace("ACTION_START_RESCHEDULE_", ""));
        if (!accessGuard.isAppointmentPatient(apptId)) {
            return AiChatResponse.builder().response("You can only reschedule your own appointments.")
                    .success(false).build();
        }
        Appointment appt = appointmentRepository.findById(apptId).orElseThrow();

        return AiChatResponse.builder()
                .response("When would you like to move your appointment with " + appt.getDoctor().getName() + "?")
                .success(true)
                .suggestion(AiBookingSuggestion.builder()
                        .type(AiBookingSuggestion.SuggestionType.DATES)
                        .doctorId(appt.getDoctor().getId())
                        .doctorName(appt.getDoctor().getName())
                        .specialization(appt.getDoctor().getSpecialization())
                        .appointmentId(apptId)
                        .originalDate(appt.getAppointmentDateTime().toLocalDate().toString())
                        .originalSlot(appt.getAppointmentDateTime().toLocalTime().toString().substring(0, 5))
                        .reason("RESCHEDULE:" + apptId)
                        .build())
                .build();
    }

    public AiBookingSuggestion.DoctorSuggestion findNextBookableDoctor(String specialization, Long preferredDoctorId,
            boolean allowPreferred) {
        List<Doctor> doctors = specialization == null ? List.of()
                : doctorRepository.findBookableBySpecialization(specialization);
        if (allowPreferred && preferredDoctorId != null) {
            for (Doctor doctor : doctors) {
                if (doctor.getId().equals(preferredDoctorId)) {
                    AiBookingSuggestion.DoctorSuggestion preferred = mapToDoctorSuggestionWithFirstSlot(doctor);
                    if (preferred.getRecommendedSlot() != null) {
                        return preferred;
                    }
                }
            }
        }
        for (Doctor doctor : doctors) {
            if (preferredDoctorId != null && doctor.getId().equals(preferredDoctorId)) {
                continue;
            }
            AiBookingSuggestion.DoctorSuggestion suggestion = mapToDoctorSuggestionWithFirstSlot(doctor);
            if (suggestion.getRecommendedSlot() != null) {
                return suggestion;
            }
        }
        return null;
    }

    public AiBookingSuggestion.DoctorSuggestion mapToDoctorSuggestion(Doctor d) {
        int totalExp = d.getExperiences() != null ? d.getExperiences().stream()
                .mapToInt(Experience::getYearsOfService).sum() : 0;
        boolean onLeave = doctorLeaveService.isDoctorOnLeave(d.getId(), LocalDate.now());
        return AiBookingSuggestion.DoctorSuggestion.builder()
                .id(d.getId()).name(d.getName()).specialization(d.getSpecialization())
                .consultationFee(d.getConsultationFees()).profileImageUrl(d.getProfileImageUrl())
                .languages(d.getLanguages()).experience(totalExp).isOnLeave(onLeave)
                .leaveMessage(onLeave ? "Away" : null)
                .isVerified(d.getIsVerified() != null && d.getIsVerified())
                .build();
    }

    private AiBookingSuggestion.DoctorSuggestion mapToDoctorSuggestionWithFirstSlot(Doctor doctor) {
        AiBookingSuggestion.DoctorSuggestion suggestion = mapToDoctorSuggestion(doctor);
        try {
            // One pass over the next 14 days (inactive doctors and leave days are skipped by the service)
            appointmentService.findFirstAvailableSlot(doctor.getId(), 14).ifPresent(slot -> {
                suggestion.setRecommendedDate(slot.date().toString());
                suggestion.setRecommendedSlot(slot.time());
            });
        } catch (Exception ignored) {
            // No suggestion for this doctor
        }
        return suggestion;
    }
}
