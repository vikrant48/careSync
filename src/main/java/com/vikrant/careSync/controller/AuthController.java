package com.vikrant.careSync.controller;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.security.dto.*;
import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.service.AuthenticationService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.service.EmailVerificationService;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
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
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final JwtService jwtService;
    private final UserService userService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthenticationResponse response = authenticationService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            var refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                    .orElseThrow(() -> new RuntimeException("Refresh token not found"));

            refreshTokenService.verifyExpiration(refreshToken);

            // Load user details to generate new JWT
            String username = refreshToken.getUsername();
            String userType = refreshToken.getUserType();

            // Generate new access token using JwtService
            UserDetails userDetails = loadUserDetails(username, userType);
            String newAccessToken = jwtService.generateToken(userDetails);

            // Delete old refresh token and create new one for security
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());
            var newRefreshToken = refreshTokenService.createRefreshToken(username, userType);

            // Get complete user data
            Object userData = getUserData(username, userType);

            RefreshTokenResponse response = RefreshTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken.getToken())
                    .tokenType("Bearer")
                    .username(username)
                    .role(userType)
                    .user(userData)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequest request) {
        try {
            refreshTokenService.deleteRefreshToken(request.getRefreshToken());

            LogoutResponse response = LogoutResponse.builder()
                    .message("Successfully logged out")
                    .success(true)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private String getClientIPAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    // Helper method to get the current username from JWT token
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
        if (AppConstants.Roles.DOCTOR.equals(userType)) {
            var doctor = userService.getUserByUsername(username);
            return org.springframework.security.core.userdetails.User.builder()
                    .username(username)
                    .password("") // Password not needed for token generation
                    .authorities(AppConstants.Roles.ROLE_DOCTOR)
                    .build();
        } else if (AppConstants.Roles.PATIENT.equals(userType)) {
            var patient = userService.getUserByUsername(username);
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
            return new com.vikrant.careSync.dto.DoctorDto(doctor);
        } else if (AppConstants.Roles.PATIENT.equals(role)) {
            var patient = patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            return new com.vikrant.careSync.dto.PatientDto(patient);
        }
        throw new RuntimeException("Invalid role: " + role);
    }
}