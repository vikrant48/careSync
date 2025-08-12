package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExperienceRequest {
    @NotBlank(message = "Hospital name is required")
    private String hospitalName;
    
    @NotBlank(message = "Position is required")
    private String position;
    
    @NotNull(message = "Years of service is required")
    @Positive(message = "Years of service must be positive")
    private Integer yearsOfService;
    
    private String details;
}
