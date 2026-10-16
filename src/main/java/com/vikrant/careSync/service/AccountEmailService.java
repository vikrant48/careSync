package com.vikrant.careSync.service;

import com.vikrant.careSync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Rules for changing an account's login email. The email is used for password reset,
 * so a new address must be unused and verified through the email OTP flow
 * (/api/auth/email-verification/start and /verify) before it can be set.
 */
@Service
@RequiredArgsConstructor
public class AccountEmailService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    /**
     * @return the new email to store, or null when the request does not change the email
     */
    public String validateEmailChange(String currentEmail, String requestedEmail) {
        if (requestedEmail == null || requestedEmail.isBlank()) {
            return null;
        }
        String newEmail = requestedEmail.trim();
        if (newEmail.equalsIgnoreCase(currentEmail)) {
            return null;
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new RuntimeException("Email already exists");
        }
        if (!emailVerificationService.isVerified(newEmail)) {
            throw new RuntimeException("Please verify the new email address before changing it");
        }
        return newEmail;
    }
}
