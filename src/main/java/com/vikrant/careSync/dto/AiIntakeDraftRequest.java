package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiIntakeDraftRequest {
    @NotBlank
    private String narrative;
}
