package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.MedicalHistory;
import lombok.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorAppointmentResponse {

    private Long appointmentId;
    private String patientName;
    private String patientEmail;
    private String patientContactInfo;
    private String patientIllnessDetails;
    private String appointmentDate;
    private String appointmentTime;
    private String status;
    private String reason;
    private String createdAt;
    private String updatedAt;
    private String statusChangedAt;
    private String statusChangedBy;
    private List<PatientMedicalHistoryDto> medicalHistory;

    public DoctorAppointmentResponse(Appointment appointment) {
        this.appointmentId = appointment.getId();
        if (appointment.getPatient() != null) {
            this.patientName = appointment.getPatient().getName();
            this.patientEmail = appointment.getPatient().getEmail();
            this.patientContactInfo = appointment.getPatient().getContactInfo();
            this.patientIllnessDetails = appointment.getPatient().getIllnessDetails();
        }
        if (appointment.getAppointmentDateTime() != null) {
            this.appointmentDate = appointment.getAppointmentDateTime().toLocalDate().toString();
            this.appointmentTime = appointment.getAppointmentDateTime().toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        this.status = appointment.getStatus() != null ? appointment.getStatus().name() : null;
        this.reason = appointment.getReason();
        
        // Format audit fields
        if (appointment.getCreatedAt() != null) {
            this.createdAt = appointment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (appointment.getUpdatedAt() != null) {
            this.updatedAt = appointment.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (appointment.getStatusChangedAt() != null) {
            this.statusChangedAt = appointment.getStatusChangedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        this.statusChangedBy = appointment.getStatusChangedBy();
        
        // Convert medical history to DTOs
        if (appointment.getPatient() != null && appointment.getPatient().getMedicalHistories() != null) {
            this.medicalHistory = appointment.getPatient().getMedicalHistories().stream()
                    .map(PatientMedicalHistoryDto::new)
                    .collect(Collectors.toList());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientMedicalHistoryDto {
        private Long id;
        private String visitDate;
        private String symptoms;
        private String diagnosis;
        private String treatment;

        public PatientMedicalHistoryDto(MedicalHistory medicalHistory) {
            this.id = medicalHistory.getId();
            this.visitDate = medicalHistory.getVisitDate().toString();
            this.symptoms = medicalHistory.getSymptoms();
            this.diagnosis = medicalHistory.getDiagnosis();
            this.treatment = medicalHistory.getTreatment();
        }
    }
}
