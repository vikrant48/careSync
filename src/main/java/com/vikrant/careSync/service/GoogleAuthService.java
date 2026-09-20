package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.dto.AuthenticationResponse;
import com.vikrant.careSync.security.dto.GoogleLoginRequest;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.service.RefreshTokenService;
import com.vikrant.careSync.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityService securityService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Transactional
    public AuthenticationResponse authenticateGoogleUser(GoogleLoginRequest request, String ipAddress,
            String userAgent) {
        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new RuntimeException("Google ID Token is required");
        }

        // Call Google OAuth TokenInfo API to verify the ID token
        String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + request.getIdToken();
        Map<String, Object> tokenClaims;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(tokenInfoUrl, Map.class);
            tokenClaims = response.getBody();
        } catch (Exception e) {
            log.error("Failed to verify Google ID token: {}", e.getMessage());
            throw new RuntimeException("Invalid or expired Google ID Token");
        }

        if (tokenClaims == null || !tokenClaims.containsKey("email")) {
            throw new RuntimeException("Invalid Google token payload");
        }

        String email = (String) tokenClaims.get("email");
        Object emailVerifiedObj = tokenClaims.get("email_verified");
        boolean emailVerified = emailVerifiedObj != null &&
                ("true".equalsIgnoreCase(String.valueOf(emailVerifiedObj)) || Boolean.TRUE.equals(emailVerifiedObj));

        if (!emailVerified) {
            throw new RuntimeException("Google account email is not verified");
        }

        String name = (String) tokenClaims.getOrDefault("name", "Google User");
        String givenName = (String) tokenClaims.getOrDefault("given_name", "");
        String familyName = (String) tokenClaims.getOrDefault("family_name", "");
        String picture = (String) tokenClaims.getOrDefault("picture", "");

        // Find existing user or register new Google user with requested role
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = registerGoogleUser(email, name, givenName, familyName, picture, request.getRole());
        }

        String roleStr = user.getRole() != null ? user.getRole().name() : AppConstants.Roles.PATIENT;

        return generateAuthResponse(user, roleStr, "Google login successful", ipAddress, userAgent);
    }

    private User registerGoogleUser(String email, String fullName, String givenName, String familyName, String picture,
            String requestedRole) {
        Long userId = generateSixDigitUserId();

        // Generate a clean username from email
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        String username = baseUsername;
        int count = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + count++;
        }

        // Determine Role (DOCTOR vs PATIENT)
        User.Role targetRole = User.Role.PATIENT;
        if (requestedRole != null && requestedRole.equalsIgnoreCase(AppConstants.Roles.DOCTOR)) {
            targetRole = User.Role.DOCTOR;
        }

        User user = User.builder()
                .id(userId)
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .email(email)
                .role(targetRole)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(user);

        String firstName = !givenName.isBlank() ? givenName : fullName;

        if (targetRole == User.Role.DOCTOR) {
            Doctor doctor = new Doctor();
            doctor.setUser(savedUser);
            doctor.setFirstName(firstName);
            doctor.setLastName(familyName);
            doctor.setProfileImageUrl(picture);
            doctor.setSpecialization("General Practice");
            doctor.setIsActive(true);
            doctorRepository.save(doctor);
        } else {
            Patient patient = new Patient();
            patient.setUser(savedUser);
            patient.setFirstName(firstName);
            patient.setLastName(familyName);
            patient.setProfileImageUrl(picture);
            patient.setIsActive(true);
            patientRepository.save(patient);
        }

        log.info("Registered new Google user ({}) with email: {} and username: {}", targetRole, email, username);
        return savedUser;
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

    private AuthenticationResponse generateAuthResponse(User user, String role, String message, String ipAddress,
            String userAgent) {
        String roleName = "ROLE_" + role;
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(roleName)
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();

        UserSession userSession = securityService.createUserSession(user.getUsername(), ipAddress, userAgent, role);
        String sessionId = userSession.getSessionId();

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("sessionId", sessionId);
        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        var refreshToken = refreshTokenService.createRefreshToken(user.getUsername(), role);

        Object userData = null;
        if (AppConstants.Roles.PATIENT.equals(role)) {
            var patient = patientRepository.findByUsername(user.getUsername()).orElse(null);
            if (patient != null) {
                userData = new PatientDto(patient);
            }
        } else if (AppConstants.Roles.DOCTOR.equals(role)) {
            var doctor = doctorRepository.findByUsername(user.getUsername()).orElse(null);
            if (doctor != null) {
                userData = new DoctorDto(doctor);
            }
        }

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(role)
                .user(userData)
                .message(message)
                .build();
    }
}
