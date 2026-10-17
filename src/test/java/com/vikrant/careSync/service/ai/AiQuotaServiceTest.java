package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.access.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Each user has a per-minute and per-day AI allowance; background calls and other users are not affected. */
class AiQuotaServiceTest {

    private final Map<String, Long> counters = new HashMap<>();
    private StringRedisTemplate redis;
    private CurrentUserService currentUser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        currentUser = mock(CurrentUserService.class);
        when(currentUser.currentUsername()).thenReturn(Optional.of("patientA"));
    }

    @Test
    void perMinuteAllowance() {
        AiQuotaService quota = new AiQuotaService(redis, currentUser, true, 3, 100);
        quota.consume();
        quota.consume();
        quota.consume();
        AiQuotaExceededException e = assertThrows(AiQuotaExceededException.class, quota::consume);
        assertTrue(e.userMessage().contains("wait a minute"));
    }

    @Test
    void perDayAllowance() {
        AiQuotaService quota = new AiQuotaService(redis, currentUser, true, 100, 2);
        quota.consume();
        quota.consume();
        AiQuotaExceededException e = assertThrows(AiQuotaExceededException.class, quota::consume);
        assertTrue(e.userMessage().contains("today's AI limit"));
    }

    @Test
    void usersHaveSeparateAllowances() {
        AiQuotaService quota = new AiQuotaService(redis, currentUser, true, 1, 100);
        quota.consume();
        when(currentUser.currentUsername()).thenReturn(Optional.of("patientB"));
        assertDoesNotThrow(quota::consume);
    }

    @Test
    void backgroundCallsAndDisabledQuotaAreNotCounted() {
        when(currentUser.currentUsername()).thenReturn(Optional.empty());
        new AiQuotaService(redis, currentUser, true, 1, 1).consume();
        new AiQuotaService(redis, currentUser, true, 1, 1).consume();

        when(currentUser.currentUsername()).thenReturn(Optional.of("patientA"));
        AiQuotaService disabled = new AiQuotaService(redis, currentUser, false, 1, 1);
        disabled.consume();
        disabled.consume();
        assertTrue(counters.isEmpty());
    }

    @Test
    void stillLimitsWhileRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        AiQuotaService quota = new AiQuotaService(redis, currentUser, true, 2, 100);
        quota.consume();
        quota.consume();
        assertThrows(AiQuotaExceededException.class, quota::consume);
    }
}
