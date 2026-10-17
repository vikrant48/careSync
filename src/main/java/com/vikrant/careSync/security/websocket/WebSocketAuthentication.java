package com.vikrant.careSync.security.websocket;

import com.vikrant.careSync.security.CustomUserDetailsService;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;
import java.util.function.Supplier;
import com.vikrant.careSync.util.ErrorLog;

/**
 * JWT authentication for STOMP sessions, using the same rules as JwtAuthenticationFilter,
 * plus a helper to run AccessGuard checks as the session's user (STOMP frames are handled on
 * broker threads where the HTTP SecurityContext is not available).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthentication {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityService securityService;

    /** @return the authenticated user for a valid, unexpired token of an active account and live session; empty otherwise */
    public Optional<Authentication> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String jwt = authorizationHeader.substring(7);
        try {
            String username = jwtService.extractUsername(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled() || !jwtService.isTokenValid(jwt, userDetails)
                    || !securityService.isSessionUsable(jwtService.extractSessionId(jwt))) {
                return Optional.empty();
            }
            return Optional.of(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        } catch (Exception e) {
            ErrorLog.debug(e, "WebSocket token rejected");
            return Optional.empty();
        }
    }

    /** Runs the action with the session user as the current SecurityContext, then restores the previous one. */
    public <T> T runAs(Principal principal, Supplier<T> action) {
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket session is not authenticated");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
