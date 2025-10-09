package com.vikrant.careSync.security.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordWithOtpRequest {
    private String email;
    private String otp;
    private String newPassword;
    private String confirmPassword;
}