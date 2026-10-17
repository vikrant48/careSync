package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.access.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-user AI allowance (calls to a model per minute and per day). The providers' free tiers are shared by every
 * user, so one person or a runaway loop must not use them up. Counted in Redis (survives restarts, shared by
 * instances); an in-memory count takes over while Redis is down. Calls made without a logged-in user (background
 * jobs) are not counted.
 */
@Service
@Slf4j
public class AiQuotaService {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "AI:QUOTA:";

    private final StringRedisTemplate redisTemplate;
    private final CurrentUserService currentUserService;
    private final boolean enabled;
    private final int perMinute;
    private final int perDay;
    private final Map<String, AtomicLong> fallbackCounts = new ConcurrentHashMap<>();

    public AiQuotaService(StringRedisTemplate redisTemplate, CurrentUserService currentUserService,
            @Value("${app.ai.quota.enabled:true}") boolean enabled,
            @Value("${app.ai.quota.per-minute:10}") int perMinute,
            @Value("${app.ai.quota.per-day:150}") int perDay) {
        this.redisTemplate = redisTemplate;
        this.currentUserService = currentUserService;
        this.enabled = enabled;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /** Counts one model call for the current user; throws when the minute or day allowance is used up. */
    public void consume() {
        if (!enabled) {
            return;
        }
        String user = currentUserService.currentUsername().orElse(null);
        if (user == null || user.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long minuteCount = increment(KEY_PREFIX + user + ":m:" + now.format(MINUTE), Duration.ofMinutes(2));
        if (minuteCount > perMinute) {
            throw new AiQuotaExceededException(
                    "You're sending AI requests too quickly. Please wait a minute and try again.");
        }
        long dayCount = increment(KEY_PREFIX + user + ":d:" + now.format(DAY), Duration.ofHours(26));
        if (dayCount > perDay) {
            throw new AiQuotaExceededException(
                    "You've reached today's AI limit. It resets at midnight (UTC); the rest of CareSync keeps working.");
        }
    }

    private long increment(String key, Duration ttl) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return value != null ? value : 0L;
        } catch (Exception e) {
            log.warn("Redis unavailable for AI quota, counting in memory");
            pruneFallback();
            return fallbackCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /** Keeps only the current minute's and day's in-memory counters */
    private void pruneFallback() {
        if (fallbackCounts.size() < 1000) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String minute = ":m:" + now.format(MINUTE);
        String day = ":d:" + now.format(DAY);
        for (Iterator<String> it = fallbackCounts.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            if (!key.endsWith(minute) && !key.endsWith(day)) {
                it.remove();
            }
        }
    }
}
