package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeAppointmentStatusRequest {
    
    @NotNull(message = "New status is required")
    private Appointment.Status newStatus;
    
    private String reason; // Optional reason for status change
}
