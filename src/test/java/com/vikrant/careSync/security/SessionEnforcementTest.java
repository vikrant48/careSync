package com.vikrant.careSync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access and refresh tokens work only while their login session lives; logout ends it at once. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionEnforcementTest {

    private static final String PROTECTED = "/api/payments/preferences/me";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private CustomUserDetailsService userDetailsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private UserSessionRepository userSessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String sessionId;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(User.builder().id(9201L).username("patientA").password("x")
                .email("patientA@test.local").role(User.Role.PATIENT).isActive(true).build()));
        patient.setFirstName("Pat");
        patient.setLastName("A");
        patientRepository.save(patient);

        // What a login issues
        sessionId = securityService.createUserSession("patientA", "10.0.0.1", "test", "PATIENT").getSessionId();
        accessToken = token(sessionId);
        refreshToken = refreshTokenService.createRefreshToken("patientA", "PATIENT", sessionId).getToken();
    }

    private String token(String session) {
        UserDetails user = userDetailsService.loadUserByUsername("patientA");
        return session == null ? jwtService.generateToken(user)
                : jwtService.generateToken(new java.util.HashMap<>(Map.of("sessionId", session)), user);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void liveSessionIsAccepted() throws Exception {
        mvc.perform(get(PROTECTED).header("Authorization", bearer(accessToken))).andExpect(status().isNoContent());
    }

    @Test
    void missingLoginAnswers401() throws Exception {
        mvc.perform(get(PROTECTED)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutSessionIsRefused() throws Exception {
        mvc.perform(get(PROTECTED).header("Authorization", bearer(token(null)))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutStopsTheAccessTokenAndRefreshToken() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(PROTECTED).header("Authorization", bearer(accessToken))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshKeepsTheSessionAndRotatesTheRefreshToken() throws Exception {
        String body = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        String newAccess = json.get("accessToken").asText();

        assertEquals(sessionId, jwtService.extractSessionId(newAccess));
        mvc.perform(get(PROTECTED).header("Authorization", bearer(newAccess))).andExpect(status().isNoContent());
        // The old refresh token is single-use
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokingAllSessionsStopsExistingTokens() throws Exception {
        securityService.deactivateAllUserSessions("patientA");
        mvc.perform(get(PROTECTED).header("Authorization", bearer(accessToken))).andExpect(status().isUnauthorized());
    }

    @Test
    void idleSessionExpires() throws Exception {
        UserSession session = userSessionRepository.findBySessionIdAndActiveTrue(sessionId).orElseThrow();
        session.setLastActivity(Instant.now().minus(2, ChronoUnit.HOURS));
        userSessionRepository.saveAndFlush(session);

        mvc.perform(get(PROTECTED).header("Authorization", bearer(accessToken))).andExpect(status().isUnauthorized());
        assertTrue(userSessionRepository.findBySessionIdAndActiveTrue(sessionId).isEmpty());
    }

    @Test
    void sessionEndsAfterMaximumLifetimeEvenWhenActive() throws Exception {
        UserSession session = userSessionRepository.findBySessionIdAndActiveTrue(sessionId).orElseThrow();
        session.setLoginTime(Instant.now().minus(25, ChronoUnit.HOURS));
        session.setLastActivity(Instant.now());
        userSessionRepository.saveAndFlush(session);

        mvc.perform(get(PROTECTED).header("Authorization", bearer(accessToken))).andExpect(status().isUnauthorized());
    }
}
