package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ESignCompletionRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotBlank(message = "Doctor full name is required for signature verification")
    private String doctorNameTyped;

    @NotBlank(message = "Doctor password is required for signature re-authentication")
    private String password;

    @NotNull(message = "Declaration acceptance is required")
    private Boolean declarationAccepted;

    private String signatureImageUrl;
}
