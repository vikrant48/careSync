package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.dto.AuthenticationRequest;
import com.vikrant.careSync.security.dto.ForgotPasswordOtpRequest;
import com.vikrant.careSync.security.dto.ResetPasswordWithOtpRequest;
import com.vikrant.careSync.security.dto.VerifyOtpRequest;
import com.vikrant.careSync.security.entity.PasswordResetOtp;
import com.vikrant.careSync.security.repository.PasswordResetOtpRepository;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import com.vikrant.careSync.security.service.OtpAttemptLimiter;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationServiceOtpTest {

    private static final String EMAIL = "victim@example.com";

    private UserRepository userRepository;
    private PasswordResetOtpRepository otpRepository;
    private OtpAttemptLimiter limiter;
    private SecurityService securityService;
    private RefreshTokenService refreshTokenService;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        otpRepository = mock(PasswordResetOtpRepository.class);
        limiter = mock(OtpAttemptLimiter.class);
        securityService = mock(SecurityService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        service = new AuthenticationService(userRepository, mock(DoctorRepository.class), mock(PatientRepository.class),
                passwordEncoder, mock(com.vikrant.careSync.security.service.TokenIssuer.class), authenticationManager, refreshTokenService, securityService,
                mock(PasswordResetTokenRepository.class), otpRepository, emailService,
                mock(EmailVerificationService.class), limiter, mock(UserIdGenerator.class),
                mock(com.vikrant.careSync.security.service.AdminLoginCodeService.class));
    }

    private PasswordResetOtp otp(String code) {
        return PasswordResetOtp.builder().email(EMAIL).otp(code).used(false).verified(false)
                .createdAt(Instant.now()).expiryDate(Instant.now().plusSeconds(600)).build();
    }

    private VerifyOtpRequest verifyRequest(String code) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(EMAIL);
        request.setOtp(code);
        return request;
    }

    @Test
    void newCodeIsSixRandomDigitsAndInvalidatesOlderCodes() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(1L).username("victim").email(EMAIL).role(User.Role.PATIENT).build()));
        ForgotPasswordOtpRequest request = new ForgotPasswordOtpRequest();
        request.setEmail(EMAIL);

        service.forgotPasswordOtp(request);

        verify(otpRepository).deleteAllForEmail(EMAIL);
        ArgumentCaptor<PasswordResetOtp> saved = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(otpRepository).save(saved.capture());
        assertTrue(saved.getValue().getOtp().matches("\\d{6}"));
    }

    @Test
    void wrongCodeIsCountedAgainstTheEmail() {
        when(otpRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(otp("123456")));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.verifyOtp(verifyRequest("000000")));
        assertEquals("Invalid or expired OTP", ex.getMessage());
        verify(limiter).recordFailure("pwd_reset", EMAIL);
    }

    @Test
    void blockedEmailIsRejectedEvenWithTheRightCode() {
        when(limiter.isBlocked("pwd_reset", EMAIL)).thenReturn(true);
        when(otpRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(otp("123456")));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.verifyOtp(verifyRequest("123456")));
        assertEquals(AuthenticationService.TOO_MANY_OTP_ATTEMPTS, ex.getMessage());
        verify(otpRepository, never()).save(any());
    }

    @Test
    void expiredOrUsedCodesFail() {
        PasswordResetOtp expired = otp("123456");
        expired.setExpiryDate(Instant.now().minusSeconds(1));
        when(otpRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(expired));
        assertThrows(RuntimeException.class, () -> service.verifyOtp(verifyRequest("123456")));

        PasswordResetOtp used = otp("123456");
        used.setUsed(true);
        when(otpRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(used));
        assertThrows(RuntimeException.class, () -> service.verifyOtp(verifyRequest("123456")));
    }

    @Test
    void successfulResetRevokesAllSessionsAndClearsCounter() {
        PasswordResetOtp verified = otp("123456");
        verified.setVerified(true);
        when(otpRepository.findTopByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(verified));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(1L).username("victim").email(EMAIL).build()));
        ResetPasswordWithOtpRequest request = new ResetPasswordWithOtpRequest();
        request.setEmail(EMAIL);
        request.setOtp("123456");
        request.setNewPassword("N3w-Passw0rd!");
        request.setConfirmPassword("N3w-Passw0rd!");

        service.resetPasswordWithOtp(request);

        assertTrue(verified.isUsed());
        verify(limiter).reset("pwd_reset", EMAIL);
        verify(securityService).deactivateAllUserSessions("victim");
        verify(refreshTokenService).deleteByUsername("victim");
    }

    @Test
    void inactiveAccountIsOnlyRevealedToTheRealOwner() {
        AuthenticationRequest login = new AuthenticationRequest();
        login.setUsername("inactiveUser");
        login.setPassword("guess");
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));
        when(userRepository.findByUsername("inactiveUser")).thenReturn(Optional.of(
                User.builder().username("inactiveUser").password("hash").build()));

        when(passwordEncoder.matches("guess", "hash")).thenReturn(false);
        RuntimeException wrong = assertThrows(RuntimeException.class, () -> service.authenticate(login, "1.2.3.4", "ua"));
        assertEquals("Invalid username or password", wrong.getMessage());

        when(passwordEncoder.matches("guess", "hash")).thenReturn(true);
        RuntimeException owner = assertThrows(RuntimeException.class, () -> service.authenticate(login, "1.2.3.4", "ua"));
        assertTrue(owner.getMessage().contains("inactive"));

        verify(securityService, atLeastOnce()).isAccountLocked("inactiveUser", "1.2.3.4");
    }
}
