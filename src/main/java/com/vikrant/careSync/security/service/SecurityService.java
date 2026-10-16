package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.entity.BlockedIP;
import com.vikrant.careSync.security.entity.LoginAttempt;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.BlockedIPRepository;
import com.vikrant.careSync.security.repository.LoginAttemptRepository;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final BlockedIPRepository blockedIPRepository;
    private final UserSessionRepository userSessionRepository;

    /** Username-wide failure limit = maxLoginAttempts x this (catches attacks spread over many IPs). */
    static final int USERNAME_WIDE_LIMIT_MULTIPLIER = 4;

    @Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.security.login-attempt-window:900000}") // 15 minutes
    private long loginAttemptWindowMs;

    @Value("${app.security.session-timeout:3600000}") // 1 hour
    private long sessionTimeoutMs;

    @Value("${app.security.session-max-lifetime:86400000}") // 24 hours
    private long sessionMaxLifetimeMs;

    // lastActivity is written at most this often, not on every request
    static final Duration ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(1);

    @Value("${app.security.ip-block-duration:3600000}") // 1 hour
    private long ipBlockDurationMs;

    public void recordLoginAttempt(String username, String ipAddress, boolean successful, String userAgent) {
        LoginAttempt attempt = LoginAttempt.builder()
                .username(username)
                .ipAddress(ipAddress)
                .successful(successful)
                .timestamp(Instant.now())
                .userAgent(userAgent)
                .build();
        loginAttemptRepository.save(attempt);

        if (!successful) {
            checkAndBlockIP(ipAddress);
        }
    }

    // Active blocks, checked on every request: kept in memory and reloaded every 30 s instead of one query per
    // request. Blocks changed on this instance apply at once; other instances see them within 30 s.
    static final long BLOCK_CACHE_MILLIS = 30_000;
    private volatile java.util.Map<String, Instant> activeBlocks = java.util.Map.of();
    private volatile long activeBlocksLoadedAt;

    public boolean isIPBlocked(String ipAddress) {
        if (isLocalhost(ipAddress)) {
            return false;
        }
        if (System.currentTimeMillis() - activeBlocksLoadedAt > BLOCK_CACHE_MILLIS) {
            reloadActiveBlocks();
        }
        Instant expiresAt = activeBlocks.get(ipAddress);
        // A block ends at expiresAt even if the cleanup job has not deactivated it yet
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    private synchronized void reloadActiveBlocks() {
        java.util.Map<String, Instant> blocks = new java.util.HashMap<>();
        for (BlockedIP block : blockedIPRepository.findByActiveTrue()) {
            blocks.put(block.getIpAddress(), block.getExpiresAt() != null ? block.getExpiresAt() : Instant.MAX);
        }
        activeBlocks = blocks;
        activeBlocksLoadedAt = System.currentTimeMillis();
    }

    /** The next check reads the blocks from the database again. */
    public void refreshBlockedIpCache() {
        activeBlocksLoadedAt = 0;
    }

    private boolean isLocalhost(String ipAddress) {
        return ipAddress == null || "127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)
                || "::1".equals(ipAddress) || "localhost".equalsIgnoreCase(ipAddress);
    }

    /**
     * Locks a username for one client IP after maxLoginAttempts failures from that IP, so an attacker
     * cannot lock a real user out from elsewhere. A higher username-wide limit still stops brute force
     * spread across many IPs.
     */
    public boolean isAccountLocked(String username, String ipAddress) {
        Instant since = Instant.now().minusMillis(loginAttemptWindowMs);
        if (ipAddress != null
                && loginAttemptRepository.countFailedAttemptsByUsernameAndIP(username, ipAddress, since) >= maxLoginAttempts) {
            return true;
        }
        long usernameWideLimit = (long) maxLoginAttempts * USERNAME_WIDE_LIMIT_MULTIPLIER;
        return loginAttemptRepository.countFailedAttemptsByUsername(username, since) >= usernameWideLimit;
    }

    private void checkAndBlockIP(String ipAddress) {
        if (isLocalhost(ipAddress) || isIPBlocked(ipAddress)) {
            return;
        }
        long failedAttempts = loginAttemptRepository.countFailedAttemptsByIP(ipAddress,
                Instant.now().minusMillis(loginAttemptWindowMs));
        if (failedAttempts >= maxLoginAttempts) {
            saveBlock(ipAddress, "Too many failed login attempts", Instant.now().plusMillis(ipBlockDurationMs));
        }
    }

    /** ip_address is unique, so re-blocking reuses the existing (possibly expired) row. */
    private void saveBlock(String ipAddress, String reason, Instant expiresAt) {
        BlockedIP blockedIP = blockedIPRepository.findByIpAddress(ipAddress)
                .orElseGet(() -> BlockedIP.builder().ipAddress(ipAddress).build());
        blockedIP.setReason(reason);
        blockedIP.setBlockedAt(Instant.now());
        blockedIP.setExpiresAt(expiresAt);
        blockedIP.setActive(true);
        blockedIPRepository.save(blockedIP);
        refreshBlockedIpCache();
    }

    @Scheduled(cron = "0 5 * * * *")
    public void scheduledCleanupExpiredBlockedIPs() {
        cleanupExpiredBlockedIPs();
    }

    public UserSession createUserSession(String username, String ipAddress, String userAgent, String userType) {
        UserSession session = UserSession.builder()
                .username(username)
                .sessionId(java.util.UUID.randomUUID().toString())
                .ipAddress(ipAddress)
                .loginTime(Instant.now())
                .lastActivity(Instant.now())
                .userAgent(userAgent)
                .userType(userType)
                .active(true)
                .build();
        return userSessionRepository.save(session);
    }

    /**
     * A token is honoured only while its server-side session is active, has been used within the session
     * timeout and is younger than the maximum session lifetime. Logout, password reset and admin actions
     * end the session, which stops every access and refresh token issued for it.
     */
    @Transactional
    public boolean isSessionUsable(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Optional<UserSession> found = userSessionRepository.findBySessionIdAndActiveTrue(sessionId);
        if (found.isEmpty()) {
            return false;
        }
        UserSession session = found.get();
        Instant now = Instant.now();
        Instant lastActivity = session.getLastActivity() != null ? session.getLastActivity() : session.getLoginTime();
        boolean idleTooLong = lastActivity.isBefore(now.minusMillis(sessionTimeoutMs));
        boolean tooOld = session.getLoginTime().isBefore(now.minusMillis(sessionMaxLifetimeMs));
        if (idleTooLong || tooOld) {
            userSessionRepository.deactivateBySessionId(sessionId);
            return false;
        }
        if (lastActivity.isBefore(now.minus(ACTIVITY_WRITE_INTERVAL))) {
            userSessionRepository.updateLastActivity(sessionId, now);
        }
        return true;
    }

    @Transactional
    public void deactivateSession(String sessionId) {
        if (sessionId != null) {
            userSessionRepository.deactivateBySessionId(sessionId);
        }
    }

    @Transactional
    public void deactivateAllUserSessions(String username) {
        userSessionRepository.deactivateAllSessionsForUser(username);
    }

    public List<UserSession> getActiveSessions(String username) {
        return userSessionRepository.findByUsernameAndActiveTrue(username);
    }

    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        userSessionRepository.deactivateExpiredSessions(now.minusMillis(sessionTimeoutMs),
                now.minusMillis(sessionMaxLifetimeMs));
    }

    public void cleanupExpiredBlockedIPs() {
        List<BlockedIP> expiredIPs = blockedIPRepository.findExpiredBlockedIPs(Instant.now());
        for (BlockedIP blockedIP : expiredIPs) {
            blockedIP.setActive(false);
            blockedIPRepository.save(blockedIP);
        }
        refreshBlockedIpCache();
    }

    // Admin IP management helpers restored
    public void unblockIP(String ipAddress) {
        blockedIPRepository.findByIpAddressAndActiveTrue(ipAddress)
                .ifPresent(blockedIP -> {
                    blockedIP.setActive(false);
                    blockedIPRepository.save(blockedIP);
                });
        refreshBlockedIpCache();
    }

    public void unblockAllIPs() {
        List<BlockedIP> activeBlockedIPs = blockedIPRepository.findByActiveTrue();
        for (BlockedIP blockedIP : activeBlockedIPs) {
            blockedIP.setActive(false);
            blockedIPRepository.save(blockedIP);
        }
        refreshBlockedIpCache();
    }

    public List<BlockedIP> getAllBlockedIPs() {
        return blockedIPRepository.findByActiveTrue();
    }

    public void blockIPManually(String ipAddress, String reason, int hoursToBlock) {
        // Check if IP is already blocked
        if (isIPBlocked(ipAddress)) {
            throw new RuntimeException("IP address " + ipAddress + " is already blocked");
        }

        Instant expiresAt = Instant.now().plusMillis(hoursToBlock * 60 * 60 * 1000L); // Convert hours to milliseconds
        saveBlock(ipAddress, reason, expiresAt);
    }
}