package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.security.dto.*;
import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.service.AuthenticationService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.service.EmailVerificationService;
import com.vikrant.careSync.service.GoogleAuthService;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.security.service.TokenIssuer;
import com.vikrant.careSync.security.ClientIpResolver;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Authentication", description = "Endpoints for user registration, login, and token management")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final JwtService jwtService;
    private final TokenIssuer tokenIssuer;
    private final UserService userService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final EmailVerificationService emailVerificationService;
    private final GoogleAuthService googleAuthService;
    private final ClientIpResolver clientIpResolver;

    @io.swagger.v3.oas.annotations.Operation(summary = "Authenticate user with Google", description = "Verifies Google ID token and returns JWT access and refresh tokens")
    @PostMapping("/google")
    public ResponseEntity<?> authenticateGoogle(@Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            String ipAddress = getClientIPAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthenticationResponse response = googleAuthService.authenticateGoogleUser(request, ipAddress, userAgent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Register a new user", description = "Creates a new doctor or patient account")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthenticationResponse response = authenticationService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/email-verification/start")
    public ResponseEntity<?> startEmailVerification(@Valid @RequestBody EmailVerificationStartRequest request) {
        try {
            emailVerificationService.startVerification(request.getName(), request.getEmail(),
                    request.getMobileNumber());
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Verification OTP sent to email");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody EmailVerificationVerifyRequest request) {
        try {
            boolean ok = emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
            if (!ok) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid or expired OTP");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Email verified successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/email-verification/status")
    public ResponseEntity<?> verificationStatus(@RequestParam("email") String email) {
        try {
            boolean verified = emailVerificationService.isVerified(email);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("verified", verified);
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(authenticationService.checkAvailability(username, email));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Authenticate user", description = "Returns JWT access and refresh tokens upon successful login")
    @PostMapping("/login")
    public ResponseEntity<?> authenticate(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        try {
            String ipAddress = getClientIPAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            AuthenticationResponse response = authenticationService.authenticate(request, ipAddress, userAgent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            var refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                    .orElseThrow(() -> new RuntimeException("Refresh token not found"));

            refreshTokenService.verifyExpiration(refreshToken);

            // The refresh token only renews a session that is still live (not logged out, revoked or expired)
            String sessionId = refreshToken.getSessionId();
            if (!securityService.isSessionUsable(sessionId)) {
                refreshTokenService.deleteRefreshToken(request.getRefreshToken());
                throw new RuntimeException("Your session has ended. Please log in again.");
            }

            // Load user details to generate new JWT
            String username = refreshToken.getUsername();
            String userType = refreshToken.getUserType();

            // The old refresh token is single-use; new tokens for the same session
            UserDetails userDetails = loadUserDetails(username, userType);
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());
            TokenIssuer.IssuedTokens tokens = tokenIssuer.renewSession(userDetails, userType, sessionId);

            // Get complete user data
            Object userData = getUserData(username, userType);

            RefreshTokenResponse response = RefreshTokenResponse.builder()
                    .accessToken(tokens.accessToken())
                    .refreshToken(tokens.refreshToken())
                    .tokenType("Bearer")
                    .username(username)
                    .role(userType)
                    .user(userData)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        try {
            // End the whole session, so its access token stops working now rather than at expiry
            refreshTokenService.findByToken(request.getRefreshToken())
                    .ifPresent(token -> endSession(token.getSessionId()));
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());
            // The access token sent with the request names its session too (the refresh token may have rotated)
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                endSession(jwtService.extractSessionId(authHeader.substring(7)));
            }

            LogoutResponse response = LogoutResponse.builder()
                    .message("Successfully logged out")
                    .success(true)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            // Get username from JWT token
            String username = getCurrentUsername(httpRequest);
            authenticationService.changePassword(request, username);

            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Password changed successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authenticationService.forgotPassword(request);

            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Password reset email sent");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/forgot-password-otp")
    public ResponseEntity<?> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordOtpRequest request) {
        try {
            authenticationService.forgotPasswordOtp(request);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "If account exists, an OTP has been sent");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authenticationService.resetPassword(request);

            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Password reset successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            authenticationService.verifyOtp(request);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "OTP verified. You may reset your password");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/reset-password-otp")
    public ResponseEntity<?> resetPasswordWithOtp(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        try {
            authenticationService.resetPasswordWithOtp(request);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Password reset successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/current-user")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "User not authenticated");
                return ResponseEntity.status(401).body(errorResponse);
            }

            String username = authentication.getName();
            UserDto userDto = userService.getUserByUsername(username);
            return ResponseEntity.ok(userDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    private String getClientIPAddress(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    // Helper method to get the current username from JWT token
    private void endSession(String sessionId) {
        if (sessionId != null) {
            securityService.deactivateSession(sessionId);
            refreshTokenService.deleteBySessionId(sessionId);
        }
    }

    private String getCurrentUsername(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header is missing or invalid");
        }

        final String jwt = authHeader.substring(7);
        return jwtService.extractUsername(jwt);
    }

    // Helper method to load user details for JWT generation
    private UserDetails loadUserDetails(String username, String userType) {
        var account = userService.getUserByUsername(username);
        if (account == null) {
            throw new RuntimeException("User not found");
        }
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new RuntimeException("Your account is inactive. Please contact an administrator.");
        }

        if (AppConstants.Roles.DOCTOR.equals(userType)) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(username)
                    .password("") // Password not needed for token generation
                    .authorities(AppConstants.Roles.ROLE_DOCTOR)
                    .build();
        } else if (AppConstants.Roles.PATIENT.equals(userType)) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(username)
                    .password("") // Password not needed for token generation
                    .authorities(AppConstants.Roles.ROLE_PATIENT)
                    .build();
        }
        throw new RuntimeException("Invalid user type: " + userType);
    }

    private Object getUserData(String username, String role) {
        if (AppConstants.Roles.DOCTOR.equals(role)) {
            var doctor = doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));
            return new DoctorDto(doctor);
        } else if (AppConstants.Roles.PATIENT.equals(role)) {
            var patient = patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            return new PatientDto(patient);
        }
        throw new RuntimeException("Invalid role: " + role);
    }
}