package com.vikrant.careSync.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.vikrant.careSync.util.ErrorLog;

/**
 * Counts failed one-time-code attempts per purpose and email, independent of client IP, so a
 * 6-digit code cannot be brute-forced by rotating addresses. Uses Redis (shared across instances)
 * and falls back to an in-memory counter when Redis is unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpAttemptLimiter {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    private final Map<String, FallbackCounter> fallback = new ConcurrentHashMap<>();

    private record FallbackCounter(long count, long expiresAt) {
    }

    public boolean isBlocked(String purpose, String email) {
        return failedAttempts(purpose, email) >= MAX_FAILED_ATTEMPTS;
    }

    /** Records one failed attempt and returns the total within the window. */
    public long recordFailure(String purpose, String email) {
        String key = key(purpose, email);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return count != null ? count : 0L;
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis unavailable for OTP attempt counter, using in-memory fallback");
            long now = System.currentTimeMillis();
            fallback.values().removeIf(c -> c.expiresAt() < now);
            return fallback.merge(key, new FallbackCounter(1, now + WINDOW.toMillis()),
                    (old, one) -> new FallbackCounter(old.count() + 1, old.expiresAt())).count();
        }
    }

    public void reset(String purpose, String email) {
        String key = key(purpose, email);
        fallback.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis unavailable while resetting OTP attempt counter");
        }
    }

    private long failedAttempts(String purpose, String email) {
        String key = key(purpose, email);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            ErrorLog.log(e);
            FallbackCounter counter = fallback.get(key);
            return counter != null && counter.expiresAt() >= System.currentTimeMillis() ? counter.count() : 0L;
        }
    }

    /** Email addresses are hashed so they do not appear as Redis keys. */
    private String key(String purpose, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "otp_fail:" + purpose + ":" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            ErrorLog.debug(e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
