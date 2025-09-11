package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.dto.*;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.security.entity.PasswordResetToken;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public AuthenticationService(DoctorRepository doctorRepository, PatientRepository patientRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService, SecurityService securityService, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.securityService = securityService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public AuthenticationResponse register(RegisterRequest request) {
        // Validate request
        validateRegistrationRequest(request);
        
        // Check if username or email already exists
        if (doctorRepository.findByUsername(request.getUsername()).isPresent() ||
            patientRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        if (doctorRepository.findByEmail(request.getEmail()).isPresent() ||
            patientRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        if ("DOCTOR".equalsIgnoreCase(request.getRole())) {
            return registerDoctor(request);
        } else if ("PATIENT".equalsIgnoreCase(request.getRole())) {
            return registerPatient(request);
        } else {
            throw new RuntimeException("Invalid role. Must be DOCTOR or PATIENT");
        }
    }

    private void validateRegistrationRequest(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().length() < 3) {
            throw new RuntimeException("Username must be at least 3 characters long");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }
        if (request.getEmail() == null || !request.getEmail().contains("@")) {
            throw new RuntimeException("Valid email is required");
        }
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new RuntimeException("First name is required");
        }
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new RuntimeException("Last name is required");
        }
    }

    private AuthenticationResponse registerDoctor(RegisterRequest request) {
        Doctor doctor = new Doctor();
        doctor.setUsername(request.getUsername());
        doctor.setPassword(passwordEncoder.encode(request.getPassword()));
        doctor.setEmail(request.getEmail());
        doctor.setRole(com.vikrant.careSync.entity.User.Role.DOCTOR);
        doctor.setFirstName(request.getFirstName());
        doctor.setLastName(request.getLastName());
        doctor.setContactInfo(request.getContactInfo());
        doctor.setProfileImageUrl(request.getProfilePictureUrl());

        if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
            try {
                doctor.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use YYYY-MM-DD");
            }
        }

        doctor.setSpecialization(request.getSpecialization());

        Doctor savedDoctor = doctorRepository.save(doctor);
        return generateAuthResponse(savedDoctor.getUsername(), "DOCTOR", "Registration successful as Doctor.");
    }

    private AuthenticationResponse registerPatient(RegisterRequest request) {
        Patient patient = new Patient();
        patient.setUsername(request.getUsername());
        patient.setPassword(passwordEncoder.encode(request.getPassword()));
        patient.setEmail(request.getEmail());
        patient.setRole(com.vikrant.careSync.entity.User.Role.PATIENT);
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setContactInfo(request.getContactInfo());
        patient.setProfileImageUrl(request.getProfilePictureUrl());

        
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
            try {
                patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use YYYY-MM-DD");
            }
        }

        patient.setIllnessDetails(request.getIllnessDetails());

        Patient savedPatient = patientRepository.save(patient);
        return generateAuthResponse(savedPatient.getUsername(), "PATIENT", "Registration successful as Patient.");
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request, String ipAddress, String userAgent) {
        // Validate request
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RuntimeException("Password is required");
        }

        // Check if IP is blocked
        if (securityService.isIPBlocked(ipAddress)) {
            throw new RuntimeException("Access denied: IP address is blocked");
        }

        // Check if an account is locked
        if (securityService.isAccountLocked(request.getUsername())) {
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, false, userAgent);
            throw new RuntimeException("Account is temporarily locked due to too many failed attempts");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // Record successful login
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, true, userAgent);

            // Update last login time
            updateLastLogin(request.getUsername());

            // Determine user type and generate response
            Doctor doctor = doctorRepository.findByUsername(request.getUsername()).orElse(null);
            if (doctor != null) {
                return generateAuthResponse(doctor.getUsername(), "DOCTOR", "Login successful as Doctor.");
            } else {
                Patient patient = patientRepository.findByUsername(request.getUsername()).orElse(null);
                if (patient != null) {
                    return generateAuthResponse(patient.getUsername(), "PATIENT", "Login successful as Patient.");
                }
            }

            throw new RuntimeException("User not found");

        } catch (Exception e) {
            // Record failed login attempt
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, false, userAgent);
            throw new RuntimeException("Invalid username or password");
        }
    }

    private void updateLastLogin(String username) {
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        if (doctor != null) {
            doctor.setLastLogin(LocalDateTime.now());
            doctorRepository.save(doctor);
        } else {
            Patient patient = patientRepository.findByUsername(username).orElse(null);
            if (patient != null) {
                patient.setLastLogin(LocalDateTime.now());
                patientRepository.save(patient);
            }
        }
    }

    public void changePassword(ChangePasswordRequest request, String username) {
        // Validate request
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
            throw new RuntimeException("Current password is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        // Find user and verify the current password
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        Patient patient = null;
        
        if (doctor == null) {
            patient = patientRepository.findByUsername(username).orElse(null);
            if (patient == null) {
                throw new RuntimeException("User not found");
            }
        }

        // Verify the current password
        String currentPassword = doctor != null ? doctor.getPassword() : patient.getPassword();
        if (!passwordEncoder.matches(request.getCurrentPassword(), currentPassword)) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Update password
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        if (doctor != null) {
            doctor.setPassword(encodedNewPassword);
            doctorRepository.save(doctor);
        } else {
            patient.setPassword(encodedNewPassword);
            patientRepository.save(patient);
        }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // Validate request
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        // Find user by email
        Doctor doctor = doctorRepository.findByEmail(request.getEmail()).orElse(null);
        Patient patient = null;
        
        if (doctor == null) {
            patient = patientRepository.findByEmail(request.getEmail()).orElse(null);
            if (patient == null) {
                // Don't reveal if email exists or not for security
                return;
            }
        }

        // Generate reset token
        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusSeconds(900); // 15 minutes expiry

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setEmail(request.getEmail());
        resetToken.setExpiryDate(expiryDate);
        resetToken.setUsed(false);
        
        passwordResetTokenRepository.save(resetToken);

        // TODO: Send email with reset link
        // For now, just log the token (in production, send email)
        System.out.println("Password reset token for " + request.getEmail() + ": " + token);

    }

    public void resetPassword(ResetPasswordRequest request) {
        // Validate request
        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new RuntimeException("Reset token is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        // Find and validate reset token
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("Reset token has already been used");
        }

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        // Find user by email
        Doctor doctor = doctorRepository.findByEmail(resetToken.getEmail()).orElse(null);
        Patient patient = null;
        
        if (doctor == null) {
            patient = patientRepository.findByEmail(resetToken.getEmail()).orElse(null);
            if (patient == null) {
                throw new RuntimeException("User not found");
            }
        }

        // Update password
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        if (doctor != null) {
            doctor.setPassword(encodedNewPassword);
            doctorRepository.save(doctor);
        } else {
            patient.setPassword(encodedNewPassword);
            patientRepository.save(patient);
        }

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    private AuthenticationResponse generateAuthResponse(String username, String role, String message) {
        UserDetails userDetails = loadUserDetails(username);
        String accessToken = jwtService.generateToken(userDetails);
        var refreshToken = refreshTokenService.createRefreshToken(username, role);

        // Get complete user data
        Object userData = getUserData(username, role);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .username(username)
                .role(role)
                .user(userData)
                .message(message)
                .build();
    }

    private Object getUserData(String username, String role) {
        if ("DOCTOR".equals(role)) {
            return doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));
        } else if ("PATIENT".equals(role)) {
            return patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
        }
        throw new RuntimeException("Invalid role: " + role);
    }

    private UserDetails loadUserDetails(String username) {
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        if (doctor != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(doctor.getUsername())
                    .password(doctor.getPassword())
                    .authorities("ROLE_DOCTOR")
                    .disabled(!doctor.getIsActive())
                    .build();
        }

        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(patient.getUsername())
                    .password(patient.getPassword())
                    .authorities("ROLE_PATIENT")
                    .disabled(!patient.getIsActive())
                    .build();
        }

        throw new RuntimeException("User not found");
    }
} 