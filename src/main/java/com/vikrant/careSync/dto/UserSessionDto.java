package com.vikrant.careSync.dto;

import com.vikrant.careSync.security.entity.UserSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSessionDto {
    private Long id;
    private String username;
    private String sessionId;
    private String ipAddress;
    private Instant loginTime;
    private Instant lastActivity;
    private boolean active;
    private String userAgent;
    private String userType;

    public UserSessionDto(UserSession session) {
        this.id = session.getId();
        this.username = session.getUsername();
        this.sessionId = session.getSessionId();
        this.ipAddress = session.getIpAddress();
        this.loginTime = session.getLoginTime();
        this.lastActivity = session.getLastActivity();
        this.active = session.isActive();
        this.userAgent = session.getUserAgent();
        this.userType = session.getUserType();
    }
}
