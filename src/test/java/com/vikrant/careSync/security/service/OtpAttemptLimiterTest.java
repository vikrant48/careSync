package com.vikrant.careSync.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OtpAttemptLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void countsInRedisWithExpiryAndBlocksAtLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
        OtpAttemptLimiter limiter = new OtpAttemptLimiter(redis);

        assertEquals(1L, limiter.recordFailure("pwd_reset", "User@Example.com"));
        verify(redis).expire(argThat(k -> k.startsWith("otp_fail:pwd_reset:") && !k.contains("example")),
                eq(Duration.ofMinutes(15)));

        when(ops.get(anyString())).thenReturn(String.valueOf(OtpAttemptLimiter.MAX_FAILED_ATTEMPTS - 1));
        assertFalse(limiter.isBlocked("pwd_reset", "user@example.com"));
        when(ops.get(anyString())).thenReturn(String.valueOf(OtpAttemptLimiter.MAX_FAILED_ATTEMPTS));
        assertTrue(limiter.isBlocked("pwd_reset", "user@example.com"));
    }

    @Test
    void fallsBackToMemoryWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        OtpAttemptLimiter limiter = new OtpAttemptLimiter(redis);

        for (int i = 0; i < OtpAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure("email_verify", "a@b.com");
        }
        assertTrue(limiter.isBlocked("email_verify", "A@B.com"), "emails are compared case-insensitively");
        assertFalse(limiter.isBlocked("pwd_reset", "a@b.com"), "purposes are counted separately");

        limiter.reset("email_verify", "a@b.com");
        assertFalse(limiter.isBlocked("email_verify", "a@b.com"));
    }
}
