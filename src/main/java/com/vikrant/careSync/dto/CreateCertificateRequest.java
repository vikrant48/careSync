package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

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
    
    private String issuingOrganization;
    
    private LocalDate issueDate;
    
    private LocalDate expiryDate;
    
    private String credentialId;
    
    private String credentialUrl;
}
