package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.FeedbackDto;
import com.vikrant.careSync.dto.MedicalHistoryDto;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.ChatRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PaymentRepository;
import com.vikrant.careSync.repository.VitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A patient's copy of their own data (right of access under GDPR / India's DPDP Act). Built from plain values,
 * never from entities, so nothing outside the patient's own record can come along through relations.
 * Unsigned clinical drafts are left out: they are not yet part of the record.
 */
@Service
@RequiredArgsConstructor
public class PatientDataExportService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentIntakeRepository intakeRepository;
    private final ChatRepository chatRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final FeedbackRepository feedbackRepository;
    private final VitalRepository vitalRepository;
    private final DocumentService documentService;
    private final PaymentPreferenceService paymentPreferenceService;
    private final com.vikrant.careSync.service.ai.AiChatHistoryService aiChatHistoryService;

    @Transactional(readOnly = true)
    public Map<String, Object> export(Patient patient) {
        Long patientId = patient.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", LocalDateTime.now());
        data.put("profile", new PatientDto(patient));

        List<Map<String, Object>> appointments = new ArrayList<>();
        List<Map<String, Object>> intakes = new ArrayList<>();
        List<Map<String, Object>> consents = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Appointment appointment : appointmentRepository.findByPatientIdWithPatientAndDoctorDetails(patientId)) {
            appointments.add(appointment(appointment));
            intakeRepository.findByAppointmentId(appointment.getId()).ifPresent(intake -> {
                intakes.add(intake(intake, appointment.getId()));
                Map<String, Object> consent = new LinkedHashMap<>();
                consent.put("appointmentId", appointment.getId());
                consent.put("consentGiven", intake.getConsentGiven());
                consent.put("confirmedAt", intake.getConfirmedAt());
                consents.add(consent);
            });
            chatRepository.findByAppointmentIdOrderByTimestampAsc(appointment.getId()).forEach(message -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("appointmentId", appointment.getId());
                row.put("from", message.getSenderRole());
                row.put("message", message.getContent());
                row.put("sentAt", message.getTimestamp());
                messages.add(row);
            });
        }
        data.put("appointments", appointments);
        data.put("preVisitIntakes", intakes);
        data.put("consents", consents);
        data.put("chatMessages", messages);

        data.put("medicalRecords", medicalHistoryRepository.findByPatientIdOrderByVisitDateDesc(patientId).stream()
                .filter(record -> Boolean.TRUE.equals(record.getIsSigned()) || Boolean.FALSE.equals(record.getIsDraft()))
                .map(MedicalHistoryDto::new)
                .toList());

        data.put("vitals", vitalRepository.findByPatientIdOrderByRecordedAtDesc(patientId).stream().map(vital -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordedAt", vital.getRecordedAt());
            row.put("systolicBP", vital.getSystolicBP());
            row.put("diastolicBP", vital.getDiastolicBP());
            row.put("sugarLevel", vital.getSugarLevel());
            row.put("weight", vital.getWeight());
            row.put("temperature", vital.getTemperature());
            row.put("heartRate", vital.getHeartRate());
            row.put("respiratoryRate", vital.getRespiratoryRate());
            return row;
        }).toList());

        // Links to files are short-lived signed links for private files
        data.put("documents", documentService.getDocumentsByPatientId(patientId).stream()
                .filter(doc -> !Boolean.FALSE.equals(doc.getIsActive()))
                .map(documentService::toDto)
                .toList());

        data.put("payments", paymentRepository.findByPatientOrderByCreatedAtDesc(patient).stream()
                .map(PaymentResponseDto::new)
                .toList());
        data.put("savedPaymentMethod", paymentPreferenceService.find(patientId).orElse(null));
        data.put("aiAssistantChats", aiChatHistoryService.exportFor(patientId));
        data.put("feedbackGiven", feedbackRepository.findByPatientId(patientId).stream()
                .map(FeedbackDto::new)
                .toList());
        return data;
    }

    private static Map<String, Object> appointment(Appointment appointment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", appointment.getId());
        row.put("dateTime", appointment.getAppointmentDateTime());
        row.put("status", appointment.getStatus());
        row.put("doctor", appointment.getDoctor() != null ? appointment.getDoctor().getName() : null);
        row.put("specialization", appointment.getDoctor() != null ? appointment.getDoctor().getSpecialization() : null);
        row.put("reason", appointment.getReason());
        row.put("cancellationReason", appointment.getCancellationReason());
        row.put("bookedVia", appointment.getBookingSource());
        row.put("createdAt", appointment.getCreatedAt());
        return row;
    }

    private static Map<String, Object> intake(AppointmentIntake intake, Long appointmentId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appointmentId", appointmentId);
        row.put("chiefComplaint", intake.getChiefComplaint());
        row.put("symptoms", intake.getSymptoms());
        row.put("duration", intake.getSymptomDuration());
        row.put("severity", intake.getSymptomSeverity());
        row.put("currentMedications", intake.getCurrentMedications());
        row.put("allergies", intake.getAllergies());
        row.put("aiSummary", intake.getAiSummary());
        row.put("redFlags", intake.getRedFlags());
        row.put("submittedAt", intake.getCreatedAt());
        return row;
    }
}
