package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.dto.*;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.security.entity.PasswordResetToken;
import com.vikrant.careSync.security.entity.PasswordResetOtp;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import com.vikrant.careSync.security.repository.PasswordResetOtpRepository;
import com.vikrant.careSync.service.interfaces.IAuthenticationService;
import com.vikrant.careSync.service.EmailService;
import com.vikrant.careSync.service.EmailVerificationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AuthenticationService implements IAuthenticationService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;

    public AuthenticationService(DoctorRepository doctorRepository, PatientRepository patientRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService, SecurityService securityService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetOtpRepository passwordResetOtpRepository, EmailService emailService,
            EmailVerificationService emailVerificationService) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.securityService = securityService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.emailService = emailService;
        this.emailVerificationService = emailVerificationService;
    }

    public AuthenticationResponse register(RegisterRequest request) {

        // Check if username or email already exists
        if (doctorRepository.findByUsername(request.getUsername()).isPresent() ||
                patientRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        if (doctorRepository.findByEmail(request.getEmail()).isPresent() ||
                patientRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // Enforce email verification before allowing registration to proceed
        if (!emailVerificationService.isVerified(request.getEmail())) {
            throw new RuntimeException("Email not verified");
        }

        if (AppConstants.Roles.DOCTOR.equalsIgnoreCase(request.getRole())) {
            return registerDoctor(request);
        } else if (AppConstants.Roles.PATIENT.equalsIgnoreCase(request.getRole())) {
            return registerPatient(request);
        } else {
            throw new RuntimeException("Invalid role. Must be DOCTOR or PATIENT");
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
        doctor.setGender(request.getGender());

        if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
            try {
                doctor.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use YYYY-MM-DD");
            }
        }

        doctor.setSpecialization(request.getSpecialization());

        Doctor savedDoctor = doctorRepository.save(doctor);

        // Attach userId to verified email record for traceability
        emailVerificationService.attachUserIdIfVerified(request.getEmail(), savedDoctor.getId());
        return generateAuthResponse(savedDoctor.getUsername(), AppConstants.Roles.DOCTOR,
                "Registration successful as Doctor.",
                "127.0.0.1", "Registration");
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
        patient.setGender(request.getGender());

        Patient savedPatient = patientRepository.save(patient);

        // Attach userId to verified email record for traceability
        emailVerificationService.attachUserIdIfVerified(request.getEmail(), savedPatient.getId());
        return generateAuthResponse(savedPatient.getUsername(), AppConstants.Roles.PATIENT,
                "Registration successful as Patient.",
                "127.0.0.1", "Registration");
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request, String ipAddress, String userAgent) {

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
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            // Record successful login
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, true, userAgent);

            // Update last login time
            updateLastLogin(request.getUsername());

            // Determine user type and generate response
            Doctor doctor = doctorRepository.findByUsername(request.getUsername()).orElse(null);
            if (doctor != null) {
                return generateAuthResponse(doctor.getUsername(), AppConstants.Roles.DOCTOR,
                        "Login successful as Doctor.", ipAddress,
                        userAgent);
            } else {
                Patient patient = patientRepository.findByUsername(request.getUsername()).orElse(null);
                if (patient != null) {
                    return generateAuthResponse(patient.getUsername(), AppConstants.Roles.PATIENT,
                            "Login successful as Patient.",
                            ipAddress, userAgent);
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
        log.info("Password reset token for {}: {}", request.getEmail(), token);

    }

    // ===================== OTP-based Reset Password Flow =====================
    @Transactional
    public void forgotPasswordOtp(com.vikrant.careSync.security.dto.ForgotPasswordOtpRequest request) {

        Doctor doctor = doctorRepository.findByEmail(request.getEmail()).orElse(null);
        Patient patient = null;
        String name;
        String role;
        Long userId;
        String mobile;

        if (doctor == null) {
            patient = patientRepository.findByEmail(request.getEmail()).orElse(null);
            if (patient == null) {
                // For security, do not reveal existence
                return;
            }
            name = patient.getName();
            role = com.vikrant.careSync.entity.User.Role.PATIENT.name();
            userId = patient.getId();
            mobile = patient.getContactInfo();
        } else {
            name = doctor.getName();
            role = com.vikrant.careSync.entity.User.Role.DOCTOR.name();
            userId = doctor.getId();
            mobile = doctor.getContactInfo();
        }

        // Delete expired OTPs for this email
        passwordResetOtpRepository.deleteExpiredForEmail(request.getEmail(), Instant.now());

        // Generate new OTP
        String otp = generateSixDigitOtp();

        PasswordResetOtp record = PasswordResetOtp.builder()
                .userId(userId)
                .name(name)
                .email(request.getEmail())
                .mobileNumber(mobile)
                .role(role)
                .otp(otp)
                .createdAt(Instant.now())
                .expiryDate(Instant.now().plusSeconds(600)) // 10 minutes
                .used(false)
                .verified(false)
                .build();
        passwordResetOtpRepository.save(record);

        // Send OTP via email service
        emailService.sendOtpEmail(request.getEmail(), name, otp);
    }

    @Transactional
    public void verifyOtp(com.vikrant.careSync.security.dto.VerifyOtpRequest request) {

        PasswordResetOtp otpRecord = passwordResetOtpRepository.findByEmailAndOtp(request.getEmail(), request.getOtp())
                .orElseThrow(() -> new RuntimeException("Invalid OTP"));

        if (otpRecord.isUsed()) {
            throw new RuntimeException("OTP has already been used");
        }
        if (otpRecord.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("OTP has expired");
        }

        // Mark verified for this record to allow reset step
        otpRecord.setVerified(true);
        passwordResetOtpRepository.save(otpRecord);
    }

    @Transactional
    public void resetPasswordWithOtp(com.vikrant.careSync.security.dto.ResetPasswordWithOtpRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        PasswordResetOtp otpRecord = passwordResetOtpRepository.findByEmailAndOtp(request.getEmail(), request.getOtp())
                .orElseThrow(() -> new RuntimeException("Invalid OTP"));

        if (otpRecord.isUsed()) {
            throw new RuntimeException("OTP has already been used");
        }
        if (!otpRecord.isVerified()) {
            throw new RuntimeException("OTP not verified");
        }
        if (otpRecord.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("OTP has expired");
        }

        // Find user by email
        Doctor doctor = doctorRepository.findByEmail(request.getEmail()).orElse(null);
        Patient patient = null;
        if (doctor == null) {
            patient = patientRepository.findByEmail(request.getEmail()).orElse(null);
            if (patient == null) {
                throw new RuntimeException("User not found");
            }
        }

        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        if (doctor != null) {
            doctor.setPassword(encodedNewPassword);
            doctorRepository.save(doctor);
        } else {
            patient.setPassword(encodedNewPassword);
            patientRepository.save(patient);
        }

        otpRecord.setUsed(true);
        passwordResetOtpRepository.save(otpRecord);
    }

    private String generateSixDigitOtp() {
        int code = (int) (Math.random() * 900000) + 100000; // 100000–999999
        return String.valueOf(code);
    }

    public void resetPassword(ResetPasswordRequest request) {
        // Validate request
        // Validate request
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

    private AuthenticationResponse generateAuthResponse(String username, String role, String message, String ipAddress,
            String userAgent) {
        UserDetails userDetails = loadUserDetails(username);

        // Create user session and get sessionId
        UserSession userSession = securityService.createUserSession(username, ipAddress, userAgent, role);
        String sessionId = userSession.getSessionId();

        // Create JWT token with sessionId in claims
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("sessionId", sessionId);
        String accessToken = jwtService.generateToken(extraClaims, userDetails);

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

    private UserDetails loadUserDetails(String username) {
        Doctor doctor = doctorRepository.findByUsername(username).orElse(null);
        if (doctor != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(doctor.getUsername())
                    .password(doctor.getPassword())
                    .authorities(AppConstants.Roles.ROLE_DOCTOR)
                    .disabled(!doctor.getIsActive())
                    .build();
        }

        Patient patient = patientRepository.findByUsername(username).orElse(null);
        if (patient != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(patient.getUsername())
                    .password(patient.getPassword())
                    .authorities(AppConstants.Roles.ROLE_PATIENT)
                    .disabled(!patient.getIsActive())
                    .build();
        }

        throw new RuntimeException("User not found");
    }
}