package com.vikrant.careSync.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentReminderRequest {
    
    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;
    
    @NotNull(message = "Reminder type is required")
    @Pattern(regexp = "EMAIL|SMS|PUSH", message = "Reminder type must be EMAIL, SMS, or PUSH")
    private String reminderType;
    
    @NotNull(message = "Hours before appointment is required")
    @Min(value = 1, message = "Hours before appointment must be at least 1")
    private Integer hoursBeforeAppointment;
}