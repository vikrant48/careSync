package com.vikrant.careSync.service;

import com.vikrant.careSync.security.service.TokenIssuer;
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
import com.vikrant.careSync.util.ErrorLog;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final UserIdGenerator userIdGenerator;
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
            ErrorLog.error(e, "Failed to verify Google ID token");
            throw new RuntimeException("Invalid or expired Google ID Token");
        }

        validateClaims(tokenClaims);
        String email = (String) tokenClaims.get("email");

        String name = (String) tokenClaims.getOrDefault("name", "Google User");
        String givenName = (String) tokenClaims.getOrDefault("given_name", "");
        String familyName = (String) tokenClaims.getOrDefault("family_name", "");
        String picture = (String) tokenClaims.getOrDefault("picture", "");

        // Find existing user or register new Google user with requested role
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = registerGoogleUser(email, name, givenName, familyName, picture, request.getRole());
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("Your account is inactive. Please contact an administrator.");
        }

        String roleStr = user.getRole() != null ? user.getRole().name() : AppConstants.Roles.PATIENT;

        return generateAuthResponse(user, roleStr, "Google login successful", ipAddress, userAgent);
    }

    /**
     * tokeninfo only proves Google issued the token; it must also have been issued to THIS app (aud),
     * otherwise any site the user signed in to with Google could replay its token here and log in as them.
     */
    void validateClaims(Map<String, Object> tokenClaims) {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.error("Google sign-in rejected: app.google.client-id (GOOGLE_CLIENT_ID) is not configured");
            throw new RuntimeException("Google sign-in is not available");
        }
        if (tokenClaims == null || !tokenClaims.containsKey("email")) {
            throw new RuntimeException("Invalid Google token payload");
        }
        if (!googleClientId.equals(String.valueOf(tokenClaims.get("aud")))) {
            log.warn("Google ID token rejected: issued for a different client (aud mismatch)");
            throw new RuntimeException("Invalid or expired Google ID Token");
        }
        String issuer = String.valueOf(tokenClaims.get("iss"));
        if (!"accounts.google.com".equals(issuer) && !"https://accounts.google.com".equals(issuer)) {
            throw new RuntimeException("Invalid or expired Google ID Token");
        }
        Object emailVerifiedObj = tokenClaims.get("email_verified");
        boolean emailVerified = emailVerifiedObj != null &&
                ("true".equalsIgnoreCase(String.valueOf(emailVerifiedObj)) || Boolean.TRUE.equals(emailVerifiedObj));
        if (!emailVerified) {
            throw new RuntimeException("Google account email is not verified");
        }
    }

    private User registerGoogleUser(String email, String fullName, String givenName, String familyName, String picture,
            String requestedRole) {
        Long userId = userIdGenerator.next();

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


    private AuthenticationResponse generateAuthResponse(User user, String role, String message, String ipAddress,
            String userAgent) {
        String roleName = "ROLE_" + role;
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(roleName)
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();

        TokenIssuer.IssuedTokens tokens = tokenIssuer.startSession(userDetails, role, ipAddress, userAgent);

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
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(role)
                .user(userData)
                .message(message)
                .build();
    }
}
