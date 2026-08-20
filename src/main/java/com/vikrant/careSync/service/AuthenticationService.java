package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
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
import org.springframework.cache.annotation.CacheEvict;
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
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class AuthenticationService implements IAuthenticationService {

    private final UserRepository userRepository;
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

    public AuthenticationService(UserRepository userRepository, DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService, SecurityService securityService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetOtpRepository passwordResetOtpRepository, EmailService emailService,
            EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
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

    private Long generateSixDigitUserId() {
        long min = 100000L;
        long max = 999999L;
        long userId;
        do {
            userId = ThreadLocalRandom.current().nextLong(min, max + 1);
        } while (userRepository.existsById(userId));
        return userId;
    }

    @CacheEvict(value = "doctorListing", allEntries = true)
    public AuthenticationResponse register(RegisterRequest request) {

        // Check if username or email already exists in users table
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
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

    public Map<String, Boolean> checkAvailability(String username, String email) {
        Map<String, Boolean> response = new HashMap<>();

        boolean usernameExists = false;
        if (username != null && !username.isEmpty()) {
            usernameExists = userRepository.existsByUsername(username);
        }

        boolean emailExists = false;
        if (email != null && !email.isEmpty()) {
            emailExists = userRepository.existsByEmail(email);
        }

        response.put("usernameAvailable", !usernameExists);
        response.put("emailAvailable", !emailExists);

        return response;
    }

    private AuthenticationResponse registerDoctor(RegisterRequest request) {
        Long userId = generateSixDigitUserId();

        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(User.Role.DOCTOR)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(savedUser);
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
        return generateAuthResponse(savedUser.getUsername(), AppConstants.Roles.DOCTOR,
                "Registration successful as Doctor.",
                "127.0.0.1", "Registration");
    }

    private AuthenticationResponse registerPatient(RegisterRequest request) {
        Long userId = generateSixDigitUserId();

        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(User.Role.PATIENT)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(user);

        Patient patient = new Patient();
        patient.setUser(savedUser);
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
        patient.setBloodGroup(request.getBloodGroup());
        patient.setIsActive(true);

        Patient savedPatient = patientRepository.save(patient);

        // Attach userId to verified email record for traceability
        emailVerificationService.attachUserIdIfVerified(request.getEmail(), savedPatient.getId());
        return generateAuthResponse(savedUser.getUsername(), AppConstants.Roles.PATIENT,
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
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String roleStr = user.getRole() != null ? user.getRole().name() : AppConstants.Roles.PATIENT;

            return generateAuthResponse(user.getUsername(), roleStr,
                    "Login successful as " + roleStr + ".", ipAddress, userAgent);

        } catch (Exception e) {
            // Record failed login attempt
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, false, userAgent);
            throw new RuntimeException("Invalid username or password");
        }
    }

    private void updateLastLogin(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
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

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            // Don't reveal if email exists or not for security
            return;
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

        log.info("Password reset token for {}: {}", request.getEmail(), token);
    }

    // ===================== OTP-based Reset Password Flow =====================
    @Transactional
    public void forgotPasswordOtp(com.vikrant.careSync.security.dto.ForgotPasswordOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return;
        }

        String name;
        String role = user.getRole() != null ? user.getRole().name() : User.Role.PATIENT.name();
        Long userId = user.getId();
        String mobile = "";

        if (user.getRole() == User.Role.DOCTOR) {
            Doctor doctor = doctorRepository.findById(userId).orElse(null);
            if (doctor != null) {
                name = doctor.getName();
                mobile = doctor.getContactInfo();
            } else {
                name = user.getUsername();
            }
        } else {
            Patient patient = patientRepository.findById(userId).orElse(null);
            if (patient != null) {
                name = patient.getName();
                mobile = patient.getContactInfo();
            } else {
                name = user.getUsername();
            }
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

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        otpRecord.setUsed(true);
        passwordResetOtpRepository.save(otpRecord);
    }

    private String generateSixDigitOtp() {
        int code = (int) (Math.random() * 900000) + 100000; // 100000–999999
        return String.valueOf(code);
    }

    public void resetPassword(ResetPasswordRequest request) {
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

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

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
        } else if (AppConstants.Roles.ADMIN.equals(role)) {
            var user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            return new com.vikrant.careSync.dto.UserSummaryDto(user);
        }
        throw new RuntimeException("Invalid role: " + role);
    }

    private UserDetails loadUserDetails(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String roleName = user.getRole() != null ? "ROLE_" + user.getRole().name() : AppConstants.Roles.ROLE_PATIENT;

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(roleName)
                .disabled(user.getIsActive() != null && !user.getIsActive())
                .build();
    }
}