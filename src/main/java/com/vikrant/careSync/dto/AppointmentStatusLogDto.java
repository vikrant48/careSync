package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatusLogDto {

    private Long id;
    private Long appointmentId;
    private Appointment.Status previousStatus;
    private Appointment.Status newStatus;
    private String changedBy;
    private String reason;
    private LocalDateTime createdAt;
}
