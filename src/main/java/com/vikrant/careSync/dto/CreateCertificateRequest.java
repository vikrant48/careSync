package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCertificateRequest {
    @NotBlank(message = "Certificate name is required")
    private String name;
    
    @NotBlank(message = "Certificate URL is required")
    private String url;
    
    private String details;
}
