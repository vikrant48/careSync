package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMedicalHistoryRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    @NotNull(message = "Visit date is required")
    private LocalDate visitDate;
    
    @NotBlank(message = "Symptoms are required")
    private String symptoms;
    
    private String diagnosis;
    
    private String treatment;
}
