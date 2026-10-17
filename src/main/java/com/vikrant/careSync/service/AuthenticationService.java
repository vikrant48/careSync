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
import com.vikrant.careSync.security.service.OtpAttemptLimiter;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.security.service.TokenIssuer;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.vikrant.careSync.util.ErrorLog;

@Service
@Slf4j
public class AuthenticationService implements IAuthenticationService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final OtpAttemptLimiter otpAttemptLimiter;
    private final UserIdGenerator userIdGenerator;

    static final String OTP_PURPOSE_PASSWORD_RESET = "pwd_reset";
    static final String TOO_MANY_OTP_ATTEMPTS =
            "Too many incorrect codes. Please wait 15 minutes and request a new code.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AuthenticationService(UserRepository userRepository, DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService, SecurityService securityService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetOtpRepository passwordResetOtpRepository, EmailService emailService,
            EmailVerificationService emailVerificationService, OtpAttemptLimiter otpAttemptLimiter,
            UserIdGenerator userIdGenerator) {
        this.otpAttemptLimiter = otpAttemptLimiter;
        this.userIdGenerator = userIdGenerator;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.securityService = securityService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.emailService = emailService;
        this.emailVerificationService = emailVerificationService;
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

        // Patients may skip email verification; doctors must always verify their email
        boolean isDoctor = AppConstants.Roles.DOCTOR.equalsIgnoreCase(request.getRole());
        boolean skipAllowed = !isDoctor && Boolean.TRUE.equals(request.getSkipEmailVerification());
        if (!skipAllowed && !emailVerificationService.isVerified(request.getEmail())) {
            throw new RuntimeException(isDoctor
                    ? "Email verification is required for doctor registration. Please verify your email."
                    : "Email not verified");
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
        Long userId = userIdGenerator.next();

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
                ErrorLog.log(e);
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
        Long userId = userIdGenerator.next();

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
                ErrorLog.log(e);
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

        // Locked per username + client IP (see SecurityService), so others cannot lock this user out
        if (securityService.isAccountLocked(request.getUsername(), ipAddress)) {
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

        } catch (org.springframework.security.authentication.DisabledException e) {
            ErrorLog.log(e);
            securityService.recordLoginAttempt(request.getUsername(), ipAddress, false, userAgent);
            // Spring checks "disabled" before the password; only reveal the account state to the real owner
            boolean passwordMatches = userRepository.findByUsername(request.getUsername())
                    .map(u -> passwordEncoder.matches(request.getPassword(), u.getPassword()))
                    .orElse(false);
            throw new RuntimeException(passwordMatches
                    ? "Your account is inactive. Please contact an administrator."
                    : "Invalid username or password");
        } catch (Exception e) {
            ErrorLog.log(e);
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

        log.info("Password reset token issued (user id {})", user.getId());
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

        // Only the newest code is ever valid: remove all earlier codes for this email
        passwordResetOtpRepository.deleteAllForEmail(request.getEmail());

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
        PasswordResetOtp otpRecord = findValidResetOtp(request.getEmail(), request.getOtp());

        // Mark verified for this record to allow reset step
        otpRecord.setVerified(true);
        passwordResetOtpRepository.save(otpRecord);
    }

    @Transactional
    public void resetPasswordWithOtp(com.vikrant.careSync.security.dto.ResetPasswordWithOtpRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        PasswordResetOtp otpRecord = findValidResetOtp(request.getEmail(), request.getOtp());
        if (!otpRecord.isVerified()) {
            throw new RuntimeException("OTP not verified");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        otpRecord.setUsed(true);
        passwordResetOtpRepository.save(otpRecord);
        otpAttemptLimiter.reset(OTP_PURPOSE_PASSWORD_RESET, request.getEmail());

        // Whoever held the old password must not keep a session
        revokeAllSessions(user.getUsername());
    }

    /**
     * Checks a password-reset code against the newest code for the email. Every wrong, expired or used
     * code counts toward OtpAttemptLimiter; after MAX_FAILED_ATTEMPTS the email is blocked for longer than
     * a code lives, so a 6-digit code cannot be brute-forced (the counter is per email, not per IP).
     */
    private PasswordResetOtp findValidResetOtp(String email, String otp) {
        if (otpAttemptLimiter.isBlocked(OTP_PURPOSE_PASSWORD_RESET, email)) {
            throw new RuntimeException(TOO_MANY_OTP_ATTEMPTS);
        }
        PasswordResetOtp otpRecord = passwordResetOtpRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElse(null);
        boolean valid = otpRecord != null
                && !otpRecord.isUsed()
                && otpRecord.getExpiryDate().isAfter(Instant.now())
                && otp != null
                && MessageDigest.isEqual(otpRecord.getOtp().getBytes(StandardCharsets.UTF_8),
                        otp.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            otpAttemptLimiter.recordFailure(OTP_PURPOSE_PASSWORD_RESET, email);
            throw new RuntimeException("Invalid or expired OTP");
        }
        return otpRecord;
    }

    private void revokeAllSessions(String username) {
        securityService.deactivateAllUserSessions(username);
        refreshTokenService.deleteByUsername(username);
    }

    private String generateSixDigitOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000)); // 000000–999999
    }

    @Transactional
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

        revokeAllSessions(user.getUsername());
    }

    private AuthenticationResponse generateAuthResponse(String username, String role, String message, String ipAddress,
            String userAgent) {
        UserDetails userDetails = loadUserDetails(username);

        // New server-side session with its access and refresh tokens
        TokenIssuer.IssuedTokens tokens = tokenIssuer.startSession(userDetails, role, ipAddress, userAgent);

        // Get complete user data
        Object userData = getUserData(username, role);

        return AuthenticationResponse.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
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