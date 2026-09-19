package com.vikrant.careSync.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {
    @NotBlank(message = "Google ID token is required")
    private String idToken;

    // Optional role if registering for the first time via Google (DOCTOR or
    // PATIENT)
    private String role;
}
