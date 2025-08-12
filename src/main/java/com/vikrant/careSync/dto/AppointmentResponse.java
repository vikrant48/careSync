package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import lombok.*;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {

    private Long appointmentId;
    private String patientName;
    private String doctorName;
    private String appointmentDate;
    private String appointmentTime;
    private String status;
    private String reason;
    private String createdAt;
    private String updatedAt;
    private String statusChangedAt;
    private String statusChangedBy;

    public AppointmentResponse(Appointment appointment) {
        this.appointmentId = appointment.getId();
        this.patientName = appointment.getPatient() != null ? appointment.getPatient().getName() : null;
        this.doctorName = appointment.getDoctor() != null ? appointment.getDoctor().getName() : null;
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
    }
}
