package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.entity.BlockedIP;
import com.vikrant.careSync.security.entity.LoginAttempt;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.BlockedIPRepository;
import com.vikrant.careSync.security.repository.LoginAttemptRepository;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final BlockedIPRepository blockedIPRepository;
    private final UserSessionRepository userSessionRepository;

    @Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.security.login-attempt-window:900000}") // 15 minutes
    private long loginAttemptWindowMs;

    @Value("${app.security.session-timeout:3600000}") // 1 hour
    private long sessionTimeoutMs;

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

    public boolean isIPBlocked(String ipAddress) {
        return blockedIPRepository.findByIpAddressAndActiveTrue(ipAddress).isPresent();
    }

    public boolean isAccountLocked(String username) {
        Instant since = Instant.now().minusMillis(loginAttemptWindowMs);
        long failedAttempts = loginAttemptRepository.countFailedAttemptsByUsername(username, since);
        return failedAttempts >= maxLoginAttempts;
    }

    private void checkAndBlockIP(String ipAddress) {
        Instant since = Instant.now().minusMillis(loginAttemptWindowMs);
        long failedAttempts = loginAttemptRepository.countFailedAttemptsByIP(ipAddress, since);
        
        if (failedAttempts >= maxLoginAttempts && !isIPBlocked(ipAddress)) {
            BlockedIP blockedIP = BlockedIP.builder()
                    .ipAddress(ipAddress)
                    .reason("Too many failed login attempts")
                    .blockedAt(Instant.now())
                    .expiresAt(Instant.now().plusMillis(ipBlockDurationMs))
                    .active(true)
                    .build();
            blockedIPRepository.save(blockedIP);
        }
    }

    public UserSession createUserSession(String username, String ipAddress, String userAgent, String userType) {
        UserSession session = UserSession.builder()
                .username(username)
                .sessionId(UUID.randomUUID().toString())
                .ipAddress(ipAddress)
                .loginTime(Instant.now())
                .lastActivity(Instant.now())
                .userAgent(userAgent)
                .userType(userType)
                .active(true)
                .build();
        return userSessionRepository.save(session);
    }

    public void updateSessionActivity(String sessionId) {
        userSessionRepository.updateLastActivity(sessionId, Instant.now());
    }

    public void deactivateSession(String sessionId) {
        userSessionRepository.findBySessionIdAndActiveTrue(sessionId)
                .ifPresent(session -> {
                    session.setActive(false);
                    userSessionRepository.save(session);
                });
    }

    public void deactivateAllUserSessions(String username) {
        userSessionRepository.deactivateAllSessionsForUser(username);
    }

    public List<UserSession> getActiveSessions(String username) {
        return userSessionRepository.findByUsernameAndActiveTrue(username);
    }

    public void cleanupExpiredSessions() {
        Instant threshold = Instant.now().minusMillis(sessionTimeoutMs);
        List<UserSession> inactiveSessions = userSessionRepository.findInactiveSessions(threshold);
        
        for (UserSession session : inactiveSessions) {
            session.setActive(false);
            userSessionRepository.save(session);
        }
    }

    public void cleanupExpiredBlockedIPs() {
        List<BlockedIP> expiredIPs = blockedIPRepository.findExpiredBlockedIPs(Instant.now());
        for (BlockedIP blockedIP : expiredIPs) {
            blockedIP.setActive(false);
            blockedIPRepository.save(blockedIP);
        }
    }

    public List<LoginAttempt> getRecentLoginAttempts(String username) {
        return loginAttemptRepository.findByUsernameOrderByTimestampDesc(username);
    }

    public List<LoginAttempt> getRecentLoginAttemptsByIP(String ipAddress) {
        return loginAttemptRepository.findByIpAddressOrderByTimestampDesc(ipAddress);
    }

    public void unblockIP(String ipAddress) {
        blockedIPRepository.findByIpAddressAndActiveTrue(ipAddress)
                .ifPresent(blockedIP -> {
                    blockedIP.setActive(false);
                    blockedIPRepository.save(blockedIP);
                });
    }

    public void unblockAllIPs() {
        List<BlockedIP> activeBlockedIPs = blockedIPRepository.findByActiveTrue();
        for (BlockedIP blockedIP : activeBlockedIPs) {
            blockedIP.setActive(false);
            blockedIPRepository.save(blockedIP);
        }
    }

    public List<BlockedIP> getAllBlockedIPs() {
        return blockedIPRepository.findByActiveTrue();
    }

    public void blockIPManually(String ipAddress, String reason, int hoursToBlock) {
        // Check if IP is already blocked
        if (isIPBlocked(ipAddress)) {
            throw new RuntimeException("IP address " + ipAddress + " is already blocked");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(hoursToBlock * 60 * 60 * 1000L); // Convert hours to milliseconds

        BlockedIP blockedIP = BlockedIP.builder()
                .ipAddress(ipAddress)
                .reason(reason)
                .blockedAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        blockedIPRepository.save(blockedIP);
    }
}