package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.entity.RefreshToken;
import com.vikrant.careSync.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration}")
    private Long refreshTokenDurationMs;

    public RefreshToken createRefreshToken(String username, String userType, String sessionId) {
        RefreshToken refreshToken = RefreshToken.builder()
                .username(username)
                .userType(userType)
                .sessionId(sessionId)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
    }

    public void deleteRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void deleteBySessionId(String sessionId) {
        if (sessionId != null) {
            refreshTokenRepository.deleteBySessionId(sessionId);
        }
    }

    @Scheduled(cron = "0 20 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }
} 