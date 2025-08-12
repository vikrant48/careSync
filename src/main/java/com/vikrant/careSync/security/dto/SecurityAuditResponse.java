package com.vikrant.careSync.security.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityAuditResponse {
    private String username;
    private List<LoginAttemptInfo> recentLoginAttempts;
    private List<SessionInfo> activeSessions;
    private boolean accountLocked;
    private boolean ipBlocked;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoginAttemptInfo {
        private String ipAddress;
        private boolean successful;
        private Instant timestamp;
        private String userAgent;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private String ipAddress;
        private Instant loginTime;
        private Instant lastActivity;
        private String userAgent;
    }
} 
 