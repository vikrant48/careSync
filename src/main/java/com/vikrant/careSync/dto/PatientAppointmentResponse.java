package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import lombok.*;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientAppointmentResponse {

    private Long appointmentId;
    private String doctorName;
    private String doctorSpecialization;
    private String doctorEmail;
    private String doctorContactInfo;
    private String appointmentDate;
    private String appointmentTime;
    private String status;
    private String reason;
    private String createdAt;
    private String updatedAt;
    private String statusChangedAt;
    private String statusChangedBy;
    private String videoRoomId;
    private Boolean isActive;

    private String doctorProfileImageUrl;

    public PatientAppointmentResponse(Appointment appointment) {
        this.appointmentId = appointment.getId();
        if (appointment.getDoctor() != null) {
            this.doctorName = appointment.getDoctor().getName();
            this.doctorSpecialization = appointment.getDoctor().getSpecialization();
            this.doctorEmail = appointment.getDoctor().getEmail();
            this.doctorContactInfo = appointment.getDoctor().getContactInfo();
            this.doctorProfileImageUrl = appointment.getDoctor().getProfileImageUrl();
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
            this.statusChangedAt = appointment.getStatusChangedAt()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        this.statusChangedBy = appointment.getStatusChangedBy();
        this.videoRoomId = appointment.getVideoRoomId();
        this.isActive = appointment.getIsActive();
    }
}
