package com.vikrant.careSync.security.service;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import com.vikrant.careSync.service.AfterCommitTaskDispatcher;
import com.vikrant.careSync.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Own sessions only; signing out ends the session and its refresh token; alerts only for new devices. */
class DeviceSessionServiceTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0 Safari/537.36";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Version/18.0 Safari/604.1";

    private UserSessionRepository sessions;
    private RefreshTokenService refreshTokens;
    private AfterCommitTaskDispatcher afterCommit;
    private DeviceSessionService service;

    @BeforeEach
    void setUp() {
        sessions = mock(UserSessionRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        afterCommit = mock(AfterCommitTaskDispatcher.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("pat")).thenReturn(Optional.of(
                User.builder().username("pat").email("pat@test.local").build()));
        service = new DeviceSessionService(sessions, refreshTokens, users, mock(EmailService.class), afterCommit);
    }

    private static UserSession session(long id, String username, String sessionId, String userAgent) {
        return UserSession.builder().id(id).username(username).sessionId(sessionId).userAgent(userAgent)
                .ipAddress("203.0.113.5").loginTime(Instant.now()).lastActivity(Instant.now()).active(true)
                .userType("PATIENT").build();
    }

    @Test
    void listsOnlyOwnSessionsAndMarksThisDevice() {
        when(sessions.findByUsernameAndActiveTrueOrderByLastActivityDesc("pat")).thenReturn(List.of(
                session(1, "pat", "s-1", CHROME_WINDOWS), session(2, "pat", "s-2", SAFARI_IPHONE)));

        List<DeviceSessionService.Device> devices = service.mySessions("pat", "s-2");

        assertEquals("Chrome on Windows", devices.get(0).device());
        assertFalse(devices.get(0).current());
        assertTrue(devices.get(1).current());
    }

    @Test
    void signingOutEndsTheSessionAndItsRefreshToken() {
        when(sessions.findById(1L)).thenReturn(Optional.of(session(1, "pat", "s-1", CHROME_WINDOWS)));

        service.signOut("pat", 1L);

        verify(sessions).deactivateBySessionId("s-1");
        verify(refreshTokens).deleteBySessionId("s-1");
    }

    @Test
    void someoneElsesSessionCannotBeSignedOut() {
        when(sessions.findById(9L)).thenReturn(Optional.of(session(9, "other", "s-9", CHROME_WINDOWS)));

        assertThrows(AccessDeniedException.class, () -> service.signOut("pat", 9L));
        assertThrows(AccessDeniedException.class, () -> service.signOut("pat", 404L));
        verify(sessions, never()).deactivateBySessionId(any());
    }

    @Test
    void signOutOthersKeepsThisDevice() {
        when(sessions.findByUsernameAndActiveTrue("pat")).thenReturn(List.of(
                session(1, "pat", "s-1", CHROME_WINDOWS), session(2, "pat", "s-2", SAFARI_IPHONE)));

        assertEquals(1, service.signOutOthers("pat", "s-2"));
        verify(refreshTokens).deleteBySessionId("s-1");
        verify(refreshTokens, never()).deleteBySessionId("s-2");
        verify(sessions).deactivateOtherSessions("pat", "s-2");
    }

    @Test
    void alertOnlyForANewKindOfDevice() {
        when(sessions.findTop50ByUsernameOrderByLoginTimeDesc("pat"))
                .thenReturn(List.of(session(1, "pat", "s-1", CHROME_WINDOWS)));

        service.alertIfNewDevice("pat", CHROME_WINDOWS.replace("130", "131"), "198.51.100.7"); // same device, updated
        verify(afterCommit, never()).submitAfterCommit(anyString(), any());

        service.alertIfNewDevice("pat", SAFARI_IPHONE, "198.51.100.7"); // new device
        verify(afterCommit).submitAfterCommit(eq("new-device sign-in alert"), any());
    }

    @Test
    void noAlertOnTheVeryFirstSignIn() {
        when(sessions.findTop50ByUsernameOrderByLoginTimeDesc("pat")).thenReturn(List.of());

        service.alertIfNewDevice("pat", SAFARI_IPHONE, "198.51.100.7");

        verify(afterCommit, never()).submitAfterCommit(anyString(), any());
    }
}
