package com.vikrant.careSync.security.service;

import com.vikrant.careSync.security.entity.BlockedIP;
import com.vikrant.careSync.security.repository.BlockedIPRepository;
import com.vikrant.careSync.security.repository.LoginAttemptRepository;
import com.vikrant.careSync.security.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityServiceTest {

    private LoginAttemptRepository loginAttemptRepository;
    private BlockedIPRepository blockedIPRepository;
    private SecurityService service;

    @BeforeEach
    void setUp() {
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        blockedIPRepository = mock(BlockedIPRepository.class);
        service = new SecurityService(loginAttemptRepository, blockedIPRepository, mock(UserSessionRepository.class));
        ReflectionTestUtils.setField(service, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(service, "loginAttemptWindowMs", 900_000L);
        ReflectionTestUtils.setField(service, "ipBlockDurationMs", 3_600_000L);
    }

    @Test
    void expiredBlockNoLongerBlocks() {
        BlockedIP expired = BlockedIP.builder().ipAddress("203.0.113.7").active(true)
                .expiresAt(Instant.now().minusSeconds(60)).build();
        when(blockedIPRepository.findByActiveTrue()).thenReturn(java.util.List.of(expired));
        assertFalse(service.isIPBlocked("203.0.113.7"));

        expired.setExpiresAt(Instant.now().plusSeconds(60));
        service.refreshBlockedIpCache();
        assertTrue(service.isIPBlocked("203.0.113.7"));
        assertFalse(service.isIPBlocked("198.51.100.1"));
        // Blocks are read once per cache period, not per request
        verify(blockedIPRepository, times(2)).findByActiveTrue(); // first check + after the refresh
        verify(blockedIPRepository, never()).findByIpAddressAndActiveTrue(anyString());
    }

    @Test
    void reblockingReusesExistingRowInsteadOfViolatingUniqueIp() {
        BlockedIP old = BlockedIP.builder().id(7L).ipAddress("203.0.113.7").active(false)
                .reason("old").blockedAt(Instant.EPOCH).expiresAt(Instant.EPOCH).build();
        when(blockedIPRepository.findByIpAddress("203.0.113.7")).thenReturn(Optional.of(old));

        service.blockIPManually("203.0.113.7", "abuse", 2);

        ArgumentCaptor<BlockedIP> saved = ArgumentCaptor.forClass(BlockedIP.class);
        verify(blockedIPRepository).save(saved.capture());
        assertSame(old, saved.getValue());
        assertTrue(saved.getValue().isActive());
        assertEquals("abuse", saved.getValue().getReason());
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now().plusSeconds(3600)));
    }

    @Test
    void lockoutIsPerUsernameAndIp() {
        when(loginAttemptRepository.countFailedAttemptsByUsernameAndIP(eq("drsmith"), eq("6.6.6.6"), any()))
                .thenReturn(5L);
        when(loginAttemptRepository.countFailedAttemptsByUsernameAndIP(eq("drsmith"), eq("203.0.113.7"), any()))
                .thenReturn(0L);
        when(loginAttemptRepository.countFailedAttemptsByUsername(eq("drsmith"), any())).thenReturn(5L);

        assertTrue(service.isAccountLocked("drsmith", "6.6.6.6"), "attacker's IP is locked");
        assertFalse(service.isAccountLocked("drsmith", "203.0.113.7"), "real user elsewhere can still log in");
    }

    @Test
    void usernameWideLimitStopsDistributedBruteForce() {
        when(loginAttemptRepository.countFailedAttemptsByUsernameAndIP(anyString(), anyString(), any())).thenReturn(1L);
        when(loginAttemptRepository.countFailedAttemptsByUsername(eq("drsmith"), any()))
                .thenReturn(5L * SecurityService.USERNAME_WIDE_LIMIT_MULTIPLIER);
        assertTrue(service.isAccountLocked("drsmith", "198.51.100.1"));
    }
}
