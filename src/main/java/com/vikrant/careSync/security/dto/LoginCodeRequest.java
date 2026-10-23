package com.vikrant.careSync.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Second login step for admins: the challenge from the login response and the emailed 6-digit code */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginCodeRequest {
    @NotBlank
    @Size(max = 64)
    private String challengeId;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "The code is 6 digits")
    private String code;
}
