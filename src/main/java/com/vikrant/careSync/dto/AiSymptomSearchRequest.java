package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** "Describe your problem" on Find & Book */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSymptomSearchRequest {
    @NotBlank(message = "Please describe your problem")
    @Size(max = 500, message = "Please keep it under 500 characters")
    private String description;
}
