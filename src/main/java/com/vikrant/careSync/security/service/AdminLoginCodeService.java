package com.vikrant.careSync.security.service;

import com.vikrant.careSync.util.ErrorLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Second login step for admins: after a correct password the admin gets a 6-digit code by email, and only the code
 * finishes the login. A challenge lives 10 minutes and works once; only a hash of the code is stored (Redis, or an
 * in-memory fallback while Redis is down). Wrong codes count toward OtpAttemptLimiter per admin email, so a code
 * cannot be brute-forced.
 */
@Service
@RequiredArgsConstructor
public class AdminLoginCodeService {

    public static final String OTP_PURPOSE = "admin-login";
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "LOGIN_MFA:";
    private static final int MAX_FALLBACK_CHALLENGES = 1_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final OtpAttemptLimiter attemptLimiter;

    /** What a challenge stores: whose login it is and the hash of the code */
    record Challenge(String username, String email, String codeHash) {
    }

    /** A new challenge: its id goes to the browser, the code goes by email */
    public record Issued(String challengeId, String code) {
    }

    private record FallbackEntry(String value, Instant expiresAt) {
    }

    private final Map<String, FallbackEntry> fallback = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FallbackEntry> eldest) {
            return size() > MAX_FALLBACK_CHALLENGES;
        }
    };

    public Issued issue(String username, String email) {
        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String value = username + "\n" + email + "\n" + hash(challengeId, code);
        String key = KEY_PREFIX + challengeId;
        try {
            redisTemplate.opsForValue().set(key, value, CODE_TTL);
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis unavailable for admin login codes, using in-memory fallback");
            synchronized (fallback) {
                fallback.put(key, new FallbackEntry(value, Instant.now().plus(CODE_TTL)));
            }
        }
        return new Issued(challengeId, code);
    }

    /**
     * The admin's username when the code is right. Wrong, expired or unknown codes throw the same message; each
     * wrong code counts toward the per-email limit, and a right code ends the challenge (single use).
     */
    public String verify(String challengeId, String code) {
        String key = KEY_PREFIX + (challengeId == null ? "" : challengeId);
        Challenge challenge = read(key).orElseThrow(() -> new IllegalArgumentException(INVALID));
        if (attemptLimiter.isBlocked(OTP_PURPOSE, challenge.email())) {
            throw new IllegalArgumentException("Too many wrong codes. Please wait 15 minutes and sign in again.");
        }
        boolean matches = code != null && code.trim().matches("\\d{6}")
                && MessageDigest.isEqual(challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                        hash(challengeId, code.trim()).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            attemptLimiter.recordFailure(OTP_PURPOSE, challenge.email());
            throw new IllegalArgumentException(INVALID);
        }
        delete(key);
        attemptLimiter.reset(OTP_PURPOSE, challenge.email());
        return challenge.username();
    }

    static final String INVALID = "Invalid or expired code. Please check the email or sign in again.";

    private Optional<Challenge> read(String key) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis unavailable for admin login codes, using in-memory fallback");
            value = null;
        }
        if (value == null) {
            synchronized (fallback) {
                FallbackEntry entry = fallback.get(key);
                if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
                    value = entry.value();
                } else if (entry != null) {
                    fallback.remove(key);
                }
            }
        }
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\n", 3);
        return parts.length == 3 ? Optional.of(new Challenge(parts[0], parts[1], parts[2])) : Optional.empty();
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis unavailable while ending an admin login challenge");
        }
        synchronized (fallback) {
            fallback.remove(key);
        }
    }

    /** The challenge id salts the hash, so equal codes never look alike */
    private static String hash(String challengeId, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
