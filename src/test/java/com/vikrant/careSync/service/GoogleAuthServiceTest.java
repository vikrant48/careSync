package com.vikrant.careSync.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthServiceTest {

    private static final String CLIENT_ID = "our-app.apps.googleusercontent.com";

    private GoogleAuthService service(String clientId) {
        GoogleAuthService service = new GoogleAuthService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "googleClientId", clientId);
        return service;
    }

    private Map<String, Object> claims(String aud, String iss, Object emailVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");
        claims.put("aud", aud);
        claims.put("iss", iss);
        claims.put("email_verified", emailVerified);
        return claims;
    }

    @Test
    void acceptsTokenIssuedForThisApp() {
        assertDoesNotThrow(() -> service(CLIENT_ID).validateClaims(claims(CLIENT_ID, "https://accounts.google.com", "true")));
        assertDoesNotThrow(() -> service(CLIENT_ID).validateClaims(claims(CLIENT_ID, "accounts.google.com", true)));
    }

    @Test
    void rejectsTokenIssuedForAnotherApp() {
        assertThrows(RuntimeException.class,
                () -> service(CLIENT_ID).validateClaims(claims("other-app.apps.googleusercontent.com", "accounts.google.com", "true")));
    }

    @Test
    void rejectsWrongIssuerOrUnverifiedEmail() {
        assertThrows(RuntimeException.class,
                () -> service(CLIENT_ID).validateClaims(claims(CLIENT_ID, "https://evil.example", "true")));
        assertThrows(RuntimeException.class,
                () -> service(CLIENT_ID).validateClaims(claims(CLIENT_ID, "accounts.google.com", "false")));
    }

    @Test
    void failsClosedWhenClientIdNotConfigured() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service("").validateClaims(claims(CLIENT_ID, "accounts.google.com", "true")));
        assertEquals("Google sign-in is not available", ex.getMessage());
    }
}
