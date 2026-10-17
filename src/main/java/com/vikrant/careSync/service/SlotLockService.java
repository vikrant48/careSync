package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import com.vikrant.careSync.util.ErrorLog;

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
        String lockValue = lockOwnerPrefix(patientId) + System.currentTimeMillis();

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
            ErrorLog.warn(e, "Redis unavailable for slot lock [{}]. Falling back to in-memory guard.", key);
            return acquireMemoryFallbackLock(key);
        }
    }

    /**
     * The slots among slotTimes that are locked: one Redis MGET for all of them instead of one call per slot.
     */
    public java.util.Set<LocalDateTime> findLockedSlots(Long doctorId, java.util.Collection<LocalDateTime> slotTimes) {
        java.util.List<LocalDateTime> slots = new java.util.ArrayList<>(slotTimes);
        java.util.Set<LocalDateTime> locked = new java.util.HashSet<>();
        if (slots.isEmpty()) {
            return locked;
        }
        java.util.List<String> keys = slots.stream().map(slot -> buildLockKey(doctorId, slot)).toList();
        try {
            java.util.List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                for (int i = 0; i < values.size() && i < slots.size(); i++) {
                    if (values.get(i) != null) {
                        locked.add(slots.get(i));
                    }
                }
            }
        } catch (Exception e) {
            ErrorLog.warn(e, "Redis check failed for {} slot locks", keys.size());
        }
        for (int i = 0; i < slots.size(); i++) {
            if (isMemoryLocked(keys.get(i))) {
                locked.add(slots.get(i));
            }
        }
        return locked;
    }

    private boolean isMemoryLocked(String key) {
        LocalDateTime expireTime = memoryLockMap.get(key);
        if (expireTime == null) {
            return false;
        }
        if (LocalDateTime.now().isBefore(expireTime)) {
            return true;
        }
        memoryLockMap.remove(key);
        return false;
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
            ErrorLog.warn(e, "Redis check failed for key [{}]", key);
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
            ErrorLog.warn(e, "Failed to delete Redis slot lock [{}]", key);
        }
        memoryLockMap.remove(key);
    }

    /**
     * Releases a hold only if it belongs to this patient. Returns false when another patient holds it.
     * The in-memory fallback does not record owners, so it is released as before when Redis is down.
     */
    public boolean releaseSlotLockIfOwner(Long doctorId, LocalDateTime slotTime, Long patientId) {
        String key = buildLockKey(doctorId, slotTime);
        try {
            String holder = redisTemplate.opsForValue().get(key);
            if (holder != null && !holder.startsWith(lockOwnerPrefix(patientId))) {
                log.warn("Patient [{}] tried to release slot lock [{}] held by someone else", patientId, key);
                return false;
            }
            if (holder != null) {
                redisTemplate.delete(key);
                log.info("Released slot lock key [{}]", key);
            }
        } catch (Exception e) {
            ErrorLog.warn(e, "Failed to release Redis slot lock [{}]", key);
        }
        memoryLockMap.remove(key);
        return true;
    }

    private String lockOwnerPrefix(Long patientId) {
        return "patient_" + patientId + "_";
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
