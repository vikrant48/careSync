package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.crypto.FieldCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Remembers AI answers by what was asked: the key is a SHA-256 of the task and the exact prompt, so a changed
 * record produces a new key (no stale answers, nothing to clear). Answers are stored encrypted because they
 * contain health details. Saves the free AI quota on pages that are opened again and again.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiResponseCache {

    private static final String KEY_PREFIX = "AI:ANSWER:";

    private final StringRedisTemplate redisTemplate;
    private final FieldCipher fieldCipher;

    /** Cached answer, or compute (and keep it when not blank). Redis problems just mean no caching. */
    public String getOrCompute(String task, String prompt, Duration ttl, Supplier<String> compute) {
        String key = KEY_PREFIX + task + ":" + sha256(task + "\n" + prompt);
        try {
            String stored = redisTemplate.opsForValue().get(key);
            if (stored != null && fieldCipher.isCurrent(stored)) {
                return fieldCipher.decryptCurrent(stored);
            }
        } catch (Exception e) {
            log.debug("AI answer cache unavailable: {}", e.getClass().getSimpleName());
        }
        String answer = compute.get();
        if (answer != null && !answer.isBlank()) {
            try {
                redisTemplate.opsForValue().set(key, fieldCipher.encrypt(answer), ttl);
            } catch (Exception e) {
                log.debug("AI answer not cached: {}", e.getClass().getSimpleName());
            }
        }
        return answer;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
