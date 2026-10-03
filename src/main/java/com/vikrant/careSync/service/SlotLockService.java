package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotLockService {

    private final StringRedisTemplate redisTemplate;

    // In-memory fallback map if Redis experiences transient connectivity issues
    private final ConcurrentHashMap<String, LocalDateTime> memoryLockMap = new ConcurrentHashMap<>();

    private static final long DEFAULT_LOCK_DURATION_MINUTES = 5;

    private String buildLockKey(Long doctorId, LocalDateTime slotTime) {
        return "slot_lock:" + doctorId + ":" + slotTime.toString();
    }

    /**
     * Attempts to acquire a temporary 5-minute lock on a doctor time slot.
     * Returns true if lock was acquired, false if slot is already locked by another
     * process.
     */
    public boolean acquireSlotLock(Long doctorId, LocalDateTime slotTime, Long patientId) {
        String key = buildLockKey(doctorId, slotTime);
        String lockValue = "patient_" + patientId + "_" + System.currentTimeMillis();

        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    lockValue,
                    Duration.ofMinutes(DEFAULT_LOCK_DURATION_MINUTES));
            if (Boolean.TRUE.equals(success)) {
                log.info("Redis slot lock acquired for key [{}], patient [{}]", key, patientId);
                return true;
            } else {
                log.warn("Slot lock conflict for key [{}] - already locked", key);
                return false;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for slot lock [{}]: {}. Falling back to in-memory guard.", key, e.getMessage());
            return acquireMemoryFallbackLock(key);
        }
    }

    /**
     * Checks whether a slot is currently locked.
     */
    public boolean isSlotLocked(Long doctorId, LocalDateTime slotTime) {
        String key = buildLockKey(doctorId, slotTime);
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(hasKey)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis check failed for key [{}]: {}", key, e.getMessage());
        }

        // Check fallback map
        LocalDateTime expireTime = memoryLockMap.get(key);
        if (expireTime != null) {
            if (LocalDateTime.now().isBefore(expireTime)) {
                return true;
            } else {
                memoryLockMap.remove(key);
            }
        }
        return false;
    }

    /**
     * Explicitly releases a slot lock after successful booking or cancellation.
     */
    public void releaseSlotLock(Long doctorId, LocalDateTime slotTime) {
        String key = buildLockKey(doctorId, slotTime);
        try {
            redisTemplate.delete(key);
            log.info("Released slot lock key [{}]", key);
        } catch (Exception e) {
            log.warn("Failed to delete Redis slot lock [{}]: {}", key, e.getMessage());
        }
        memoryLockMap.remove(key);
    }

    private synchronized boolean acquireMemoryFallbackLock(String key) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = memoryLockMap.get(key);
        if (expireTime != null && now.isBefore(expireTime)) {
            return false;
        }
        memoryLockMap.put(key, now.plusMinutes(DEFAULT_LOCK_DURATION_MINUTES));
        return true;
    }
}
