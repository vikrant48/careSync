package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMasterDataRequest {
    @NotBlank(message = "Value is required")
    private String value;

    private String code;
    private String description;
    private Long orgId;
}
