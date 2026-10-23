package com.vikrant.careSync.security.service;

import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import com.vikrant.careSync.service.AfterCommitTaskDispatcher;
import com.vikrant.careSync.service.EmailService;
import com.vikrant.careSync.util.DeviceLabel;
import com.vikrant.careSync.util.ErrorLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A user's own signed-in devices: list them, sign one out, or sign out all the others. Also emails the user when
 * someone signs in from a browser/system this account has not used before. Users only ever see and end their own
 * sessions; a signed-out device loses its session and refresh token at once.
 */
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AfterCommitTaskDispatcher afterCommit;

    /** One signed-in device as shown to its owner (no session ids: they are part of the login) */
    public record Device(Long id, String device, String ipAddress, Instant signedInAt, Instant lastActiveAt,
            boolean current) {
    }

    public List<Device> mySessions(String username, String currentSessionId) {
        return sessionRepository.findByUsernameAndActiveTrueOrderByLastActivityDesc(username).stream()
                .map(s -> new Device(s.getId(), DeviceLabel.of(s.getUserAgent()), s.getIpAddress(), s.getLoginTime(),
                        s.getLastActivity() != null ? s.getLastActivity() : s.getLoginTime(),
                        s.getSessionId().equals(currentSessionId)))
                .toList();
    }

    /** Signs out one of the user's own devices; someone else's (or an unknown) session id is refused the same way */
    @Transactional
    public boolean signOut(String username, Long id) {
        UserSession session = sessionRepository.findById(id)
                .filter(s -> s.getUsername().equals(username) && s.isActive())
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        end(session.getSessionId());
        return true;
    }

    /** Signs out every device except the one making the request; returns how many were signed out */
    @Transactional
    public int signOutOthers(String username, String currentSessionId) {
        if (currentSessionId == null || currentSessionId.isBlank()) {
            throw new IllegalStateException("This device's session could not be identified.");
        }
        List<UserSession> others = sessionRepository.findByUsernameAndActiveTrue(username).stream()
                .filter(s -> !s.getSessionId().equals(currentSessionId))
                .toList();
        others.forEach(s -> refreshTokenService.deleteBySessionId(s.getSessionId()));
        sessionRepository.deactivateOtherSessions(username, currentSessionId);
        return others.size();
    }

    private void end(String sessionId) {
        sessionRepository.deactivateBySessionId(sessionId);
        refreshTokenService.deleteBySessionId(sessionId);
    }

    /**
     * Call before the new session is saved. Emails the account owner when this browser/system was never used to
     * sign in before. The very first sign-in of an account sends nothing. Never blocks or breaks the login.
     */
    public void alertIfNewDevice(String username, String userAgent, String ipAddress) {
        try {
            List<UserSession> history = sessionRepository.findTop50ByUsernameOrderByLoginTimeDesc(username);
            if (history.isEmpty()) {
                return; // first sign-in ever
            }
            String device = DeviceLabel.of(userAgent);
            boolean known = history.stream().anyMatch(s -> DeviceLabel.of(s.getUserAgent()).equals(device));
            if (known) {
                return;
            }
            userRepository.findByUsername(username)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .ifPresent(u -> {
                        String when = WHEN.format(Instant.now());
                        String ip = DeviceLabel.maskIp(ipAddress);
                        afterCommit.submitAfterCommit("new-device sign-in alert",
                                () -> emailService.sendNewDeviceAlert(u.getEmail(), u.getUsername(), device, ip, when));
                    });
        } catch (Exception e) {
            ErrorLog.warn(e, "Could not check for a new sign-in device");
        }
    }
}
