package com.vikrant.careSync.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin login codes: right code once, wrong codes counted, nothing usable stored, works without Redis. */
class AdminLoginCodeServiceTest {

    private OtpAttemptLimiter limiter;
    private AdminLoginCodeService service;

    @BeforeEach
    void setUp() {
        // Redis down: every call throws, so the in-memory fallback is used
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("down"));
        limiter = mock(OtpAttemptLimiter.class);
        service = new AdminLoginCodeService(redis, limiter);
    }

    @Test
    void theRightCodeFinishesTheLoginOnce() {
        AdminLoginCodeService.Issued issued = service.issue("admin1", "admin@test.local");

        assertTrue(issued.code().matches("\\d{6}"));
        assertEquals("admin1", service.verify(issued.challengeId(), issued.code()));
        verify(limiter).reset(AdminLoginCodeService.OTP_PURPOSE, "admin@test.local");

        assertThrows(IllegalArgumentException.class, () -> service.verify(issued.challengeId(), issued.code()),
                "a code works only once");
    }

    @Test
    void aWrongCodeIsCountedAgainstTheAdminsEmail() {
        AdminLoginCodeService.Issued issued = service.issue("admin1", "admin@test.local");
        String wrong = issued.code().equals("000000") ? "111111" : "000000";

        assertThrows(IllegalArgumentException.class, () -> service.verify(issued.challengeId(), wrong));
        verify(limiter).recordFailure(AdminLoginCodeService.OTP_PURPOSE, "admin@test.local");
    }

    @Test
    void blockedAfterTooManyWrongCodesEvenWithTheRightOne() {
        AdminLoginCodeService.Issued issued = service.issue("admin1", "admin@test.local");
        when(limiter.isBlocked(eq(AdminLoginCodeService.OTP_PURPOSE), eq("admin@test.local"))).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.verify(issued.challengeId(), issued.code()));
        assertTrue(e.getMessage().contains("Too many wrong codes"));
    }

    @Test
    void anUnknownChallengeGetsTheSameAnswerAsAWrongCode() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.verify("not-a-real-challenge", "123456"));
        assertEquals(AdminLoginCodeService.INVALID, e.getMessage());
    }
}
