package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AppointmentFollowThroughDto;
import com.vikrant.careSync.dto.FollowUpSlotDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppointmentFollowThroughService {

    private static final Set<Appointment.Status> PATIENT_DISRUPTION = EnumSet.of(
            Appointment.Status.CANCELLED,
            Appointment.Status.CANCELLED_BY_PATIENT,
            Appointment.Status.NO_SHOW,
            Appointment.Status.NO_SHOW_PATIENT,
            Appointment.Status.REJECTED);
    private static final Set<Appointment.Status> DOCTOR_DISRUPTION = EnumSet.of(
            Appointment.Status.CANCELLED_BY_DOCTOR,
            Appointment.Status.NO_SHOW_DOCTOR);

    private final AppointmentRepository appointmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final FeedbackRepository feedbackRepository;
    private final DoctorLeaveService doctorLeaveService;
    private final AiClinicalService aiClinicalService;
    private final AiBookingService aiBookingService;

    public AppointmentFollowThroughDto getFollowThrough(Long appointmentId, Long patientId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
        if (appointment.getPatient() == null || !appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Unauthorized: Only the patient can view this follow-through");
        }

        Doctor doctor = appointment.getDoctor();
        boolean doctorUnavailable = doctor == null || !doctor.canAcceptAppointments()
                || (appointment.getAppointmentDateTime() != null
                        && doctorLeaveService.isDoctorOnLeave(doctor.getId(), appointment.getAppointmentDateTime().toLocalDate()));
        AppointmentFollowThroughDto.AppointmentFollowThroughDtoBuilder response = AppointmentFollowThroughDto.builder();

        if (appointment.getStatus() == Appointment.Status.COMPLETED) {
            MedicalHistory history = medicalHistoryRepository.findByAppointmentId(appointmentId).orElse(null);
            response.plainLanguageSummary(aiClinicalService.generatePlainLanguageVisitSummary(
                    doctor == null ? "" : doctor.getName(),
                    history == null ? "" : history.getSubjective(),
                    history == null ? "" : history.getAssessment(),
                    history == null ? "" : history.getPlan(),
                    history == null ? "" : history.getDiagnosis(),
                    history == null ? "" : history.getMedicine()))
                    .feedbackRequested(feedbackRepository.findByAppointmentId(appointmentId).isEmpty())
                    .suggestedFollowUp(toSlot(aiBookingService.findNextBookableDoctor(
                            doctor == null ? null : doctor.getSpecialization(),
                            doctor == null ? null : doctor.getId(),
                            !doctorUnavailable),
                            "Suggested follow-up with the next open slot"));
        }

        if (doctorUnavailable || PATIENT_DISRUPTION.contains(appointment.getStatus())
                || DOCTOR_DISRUPTION.contains(appointment.getStatus())) {
            boolean keepSameDoctor = PATIENT_DISRUPTION.contains(appointment.getStatus()) && !doctorUnavailable;
            String reason = doctorUnavailable
                    ? "The assigned doctor is inactive or on leave"
                    : DOCTOR_DISRUPTION.contains(appointment.getStatus())
                            ? "The doctor could not keep this visit"
                            : "This visit was cancelled or missed";
            response.replacementOffer(toSlot(aiBookingService.findNextBookableDoctor(
                    doctor == null ? null : doctor.getSpecialization(),
                    doctor == null ? null : doctor.getId(),
                    keepSameDoctor), reason));
        }
        return response.build();
    }

    private FollowUpSlotDto toSlot(AiBookingSuggestion.DoctorSuggestion suggestion, String reason) {
        if (suggestion == null || suggestion.getRecommendedSlot() == null) {
            return null;
        }
        return FollowUpSlotDto.builder()
                .doctorId(suggestion.getId())
                .doctorName(suggestion.getName())
                .specialization(suggestion.getSpecialization())
                .date(suggestion.getRecommendedDate())
                .slot(suggestion.getRecommendedSlot())
                .consultationFee(suggestion.getConsultationFee())
                .reason(reason)
                .build();
    }
}
