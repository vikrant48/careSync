package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateAppointmentRequest {
    @NotNull(message = "Doctor ID is required")
    public Long doctorId;
    
    @NotNull(message = "Appointment date and time is required")
    @Future(message = "Appointment date and time must be in the future")
    public LocalDateTime appointmentDateTime;
    
    public String reason;
}
