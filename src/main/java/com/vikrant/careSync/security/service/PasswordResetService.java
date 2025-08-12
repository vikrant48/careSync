package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.entity.PasswordResetToken;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.jwt.password-reset-expiration}")
    private Long passwordResetTokenDurationMs;

    public PasswordResetToken createPasswordResetToken(String email) {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(email)
                .expiryDate(Instant.now().plusMillis(passwordResetTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .used(false)
                .build();

        return passwordResetTokenRepository.save(resetToken);
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        return passwordResetTokenRepository.findByToken(token);
    }

    public PasswordResetToken verifyExpiration(PasswordResetToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            passwordResetTokenRepository.delete(token);
            throw new RuntimeException("Password reset token was expired. Please request a new one");
        }
        if (token.isUsed()) {
            throw new RuntimeException("Password reset token has already been used");
        }
        return token;
    }

    public void markTokenAsUsed(String token) {
        passwordResetTokenRepository.findByToken(token)
                .ifPresent(resetToken -> {
                    resetToken.setUsed(true);
                    passwordResetTokenRepository.save(resetToken);
                });
    }

    public void deleteExpiredTokens() {
        passwordResetTokenRepository.findAll().stream()
                .filter(token -> token.getExpiryDate().compareTo(Instant.now()) < 0)
                .forEach(passwordResetTokenRepository::delete);
    }
} 