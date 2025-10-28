package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BookingRequest {
    
    private Long patientId; // Optional: null for patient self-booking, required for doctor booking
    
    @NotEmpty(message = "At least one test must be selected")
    private List<Long> selectedTestIds;
    
    private String prescribedBy; // Will be set automatically based on who creates the booking
    
    private LocalDateTime bookingDate; // Optional, defaults to current time
    
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
    
    private Boolean isFullBodyCheckup = false; // If true, all available tests will be selected
}