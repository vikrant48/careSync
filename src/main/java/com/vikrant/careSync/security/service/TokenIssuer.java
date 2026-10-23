package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * The one place that issues tokens: every access token carries its server-side session id and every refresh
 * token is tied to that session (used by password login, Google login and token refresh).
 */
@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final DeviceSessionService deviceSessionService;

    public record IssuedTokens(String accessToken, String refreshToken) {
    }

    /** Login: a new session, an access token bound to it and a refresh token for it. */
    public IssuedTokens startSession(UserDetails user, String role, String ipAddress, String userAgent) {
        // Before the session is saved, so this sign-in is not counted as a known device
        deviceSessionService.alertIfNewDevice(user.getUsername(), userAgent, ipAddress);
        String sessionId = securityService.createUserSession(user.getUsername(), ipAddress, userAgent, role)
                .getSessionId();
        return issue(user, role, sessionId);
    }

    /** Refresh: new tokens for a session the caller has already checked is still live. */
    public IssuedTokens renewSession(UserDetails user, String role, String sessionId) {
        return issue(user, role, sessionId);
    }

    private IssuedTokens issue(UserDetails user, String role, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", sessionId);
        String accessToken = jwtService.generateToken(claims, user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername(), role, sessionId).getToken();
        return new IssuedTokens(accessToken, refreshToken);
    }
}
