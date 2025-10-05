package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateMedicalHistoryRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    @NotNull(message = "Visit date is required")
    private LocalDate visitDate;
    
    @NotBlank(message = "Symptoms are required")
    private String symptoms;
    
    private String diagnosis;
    
    private String treatment;
    
    // Prescription fields
    private String medicine;
    
    private String doses;
    
    private String notes;
}
