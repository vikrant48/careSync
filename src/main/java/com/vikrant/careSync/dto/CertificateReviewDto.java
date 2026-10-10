package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateReviewDto {
    private Long certificateId;
    private String extractedHolderName;
    private String extractedOrganization;
    private String extractedCredentialId;
    private String extractedIssueDate;
    private String extractedExpiryDate;
    private List<String> mismatches;
    private boolean adminApprovalRequired;
}
