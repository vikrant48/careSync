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
public class CreateEducationRequest {
    @NotBlank(message = "Degree is required")
    private String degree;
    
    @NotBlank(message = "Institution is required")
    private String institution;
    
    @NotNull(message = "Year of completion is required")
    @Positive(message = "Year of completion must be positive")
    private Integer yearOfCompletion;
    
    private String details;
}
