package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.repository.CertificateRepository;
import com.vikrant.careSync.service.AiService;
import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.service.DoctorService;
import com.vikrant.careSync.security.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.vikrant.careSync.util.ErrorLog;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AdminController {

    private final SecurityService securityService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final CertificateRepository certificateRepository;
    private final AiService aiService;
    private final DoctorService doctorService;
    private final com.vikrant.careSync.service.AccountStatusService accountStatusService;
    private final com.vikrant.careSync.service.storage.MedicalFileMigrationService medicalFileMigrationService;
    private final com.vikrant.careSync.security.crypto.FieldReencryptionService fieldReencryptionService;
    private final com.vikrant.careSync.service.AuditService auditService;

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> getAllUsersSummary() {
        List<UserSummaryDto> users = userRepository.findAll().stream()
                .map(UserSummaryDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/doctors")
    public ResponseEntity<AdminDoctorPagedResponse> getAdminDoctorList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isVerified) {
        return ResponseEntity.ok(doctorService.getAdminDoctorList(page, size, search, isVerified));
    }

    @GetMapping("/doctors/{doctorId}")
    public ResponseEntity<DoctorDto> getAdminDoctorDetails(@PathVariable Long doctorId) {
        return doctorService.getDoctorDtoById(doctorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/users/{username}/toggle-active")
    @CacheEvict(value = { "doctorListing", "DOCTOR:PROFILE", "DOCTOR:PAGINATED_LIST",
            "PATIENT:PROFILE", "PATIENT:PAGINATED_LIST", "PATIENT:APPOINTMENTS",
            "DOCTOR:APPOINTMENTS", "PATIENT:COMPLETE_DATA" }, allEntries = true)
    public ResponseEntity<Map<String, Object>> toggleUserActiveStatus(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found with username: " + username);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        User user = userOpt.get();
        boolean newStatus = !Boolean.TRUE.equals(user.getIsActive());
        accountStatusService.setActive(user.getId(), newStatus);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User " + username + " is now " + (newStatus ? "ACTIVE" : "INACTIVE"));
        response.put("username", username);
        response.put("isActive", newStatus);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{username}/status")
    @CacheEvict(value = { "doctorListing", "DOCTOR:PROFILE", "DOCTOR:PAGINATED_LIST",
            "PATIENT:PROFILE", "PATIENT:PAGINATED_LIST", "PATIENT:APPOINTMENTS",
            "DOCTOR:APPOINTMENTS", "PATIENT:COMPLETE_DATA" }, allEntries = true)
    public ResponseEntity<Map<String, Object>> setUserActiveStatus(@PathVariable String username,
            @RequestParam("active") boolean active) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found with username: " + username);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        accountStatusService.setActive(userOpt.get().getId(), active);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User " + username + " active status set to " + active);
        response.put("username", username);
        response.put("isActive", active);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/doctors/{doctorId}/verify")
    public ResponseEntity<Map<String, Object>> verifyDoctor(@PathVariable Long doctorId,
            @RequestParam(value = "verify", defaultValue = "true") boolean verify) {
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Doctor not found with ID: " + doctorId);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Doctor doctor = doctorOpt.get();
        doctor.setIsVerified(verify);
        doctorRepository.save(doctor);

        Map<String, Object> response = new HashMap<>();
        response.put("message",
                "Doctor " + doctorId + " verification status updated to: " + (verify ? "VERIFIED" : "UNVERIFIED"));
        response.put("doctorId", doctorId);
        response.put("isVerified", verify);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/doctors/{doctorId}/certificates/{certificateId}/review")
    public ResponseEntity<?> reviewCertificate(@PathVariable Long doctorId, @PathVariable Long certificateId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        if (certificate.getDoctor() == null || !doctorId.equals(certificate.getDoctor().getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Certificate does not belong to this doctor"));
        }
        return ResponseEntity.ok(aiService.reviewCertificate(doctor, certificate));
    }

    @PostMapping("/doctors/{doctorId}/verify")
    public ResponseEntity<Map<String, Object>> verifyDoctorExplicit(@PathVariable Long doctorId) {
        return verifyDoctor(doctorId, true);
    }

    @PostMapping("/doctors/{doctorId}/unverify")
    public ResponseEntity<Map<String, Object>> unverifyDoctorExplicit(@PathVariable Long doctorId) {
        return verifyDoctor(doctorId, false);
    }

    @DeleteMapping("/blocked-ips/{ipAddress}")
    public ResponseEntity<Map<String, String>> deleteBlockedIP(@PathVariable String ipAddress) {
        return unblockIP(ipAddress);
    }

    @DeleteMapping("/blocked-ips")
    public ResponseEntity<Map<String, String>> deleteAllBlockedIPs() {
        return unblockAllIPs();
    }

    @GetMapping("/blocked-ips")
    public ResponseEntity<List<BlockedIPDto>> getAllBlockedIPs() {
        try {
            List<BlockedIPDto> blockedIPs = securityService.getAllBlockedIPs().stream().map(BlockedIPDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(blockedIPs);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/unblock-ip/{ipAddress}")
    public ResponseEntity<Map<String, String>> unblockIP(@PathVariable String ipAddress) {
        try {
            securityService.unblockIP(ipAddress);
            Map<String, String> response = new HashMap<>();
            response.put("message", "IP address " + ipAddress + " has been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/unblock-all-ips")
    public ResponseEntity<Map<String, String>> unblockAllIPs() {
        try {
            securityService.unblockAllIPs();
            Map<String, String> response = new HashMap<>();
            response.put("message", "All IP addresses have been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/cleanup-expired")
    public ResponseEntity<Map<String, String>> cleanupExpired() {
        try {
            securityService.cleanupExpiredBlockedIPs();
            securityService.cleanupExpiredSessions();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Expired blocked IPs and sessions have been cleaned up");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/block-doctor/{doctorId}")
    @CacheEvict(value = { "doctorListing", "DOCTOR:PROFILE", "DOCTOR:PAGINATED_LIST",
            "PATIENT:APPOINTMENTS", "DOCTOR:APPOINTMENTS" }, allEntries = true)
    public ResponseEntity<Map<String, String>> blockDoctor(@PathVariable Long doctorId,
            @RequestParam(required = false) String reason) {
        try {
            Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
            if (doctorOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Doctor not found with ID: " + doctorId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Doctor doctor = doctorOpt.get();
            accountStatusService.setActive(doctor.getId(), false);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Doctor " + doctor.getUsername() + " has been blocked");
            response.put("reason", reason != null ? reason : "No reason provided");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/block-patient/{patientId}")
    @CacheEvict(value = { "PATIENT:PROFILE", "PATIENT:PAGINATED_LIST", "PATIENT:APPOINTMENTS",
            "DOCTOR:APPOINTMENTS", "PATIENT:COMPLETE_DATA" }, allEntries = true)
    public ResponseEntity<Map<String, String>> blockPatient(@PathVariable Long patientId,
            @RequestParam(required = false) String reason) {
        try {
            Optional<Patient> patientOpt = patientRepository.findById(patientId);
            if (patientOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Patient not found with ID: " + patientId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Patient patient = patientOpt.get();
            accountStatusService.setActive(patient.getId(), false);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Patient " + patient.getUsername() + " has been blocked");
            response.put("reason", reason != null ? reason : "No reason provided");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/unblock-doctor/{doctorId}")
    @CacheEvict(value = { "doctorListing", "DOCTOR:PROFILE", "DOCTOR:PAGINATED_LIST",
            "PATIENT:APPOINTMENTS", "DOCTOR:APPOINTMENTS" }, allEntries = true)
    public ResponseEntity<Map<String, String>> unblockDoctor(@PathVariable Long doctorId) {
        try {
            Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
            if (doctorOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Doctor not found with ID: " + doctorId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Doctor doctor = doctorOpt.get();
            accountStatusService.setActive(doctor.getId(), true);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Doctor " + doctor.getUsername() + " has been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/unblock-patient/{patientId}")
    @CacheEvict(value = { "PATIENT:PROFILE", "PATIENT:PAGINATED_LIST", "PATIENT:APPOINTMENTS",
            "DOCTOR:APPOINTMENTS", "PATIENT:COMPLETE_DATA" }, allEntries = true)
    public ResponseEntity<Map<String, String>> unblockPatient(@PathVariable Long patientId) {
        try {
            Optional<Patient> patientOpt = patientRepository.findById(patientId);
            if (patientOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Patient not found with ID: " + patientId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Patient patient = patientOpt.get();
            accountStatusService.setActive(patient.getId(), true);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Patient " + patient.getUsername() + " has been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/block-ip")
    public ResponseEntity<Map<String, String>> blockIP(@RequestParam String ipAddress,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false, defaultValue = "24") int hoursToBlock) {
        try {
            String blockReason = reason != null ? reason : "Manually blocked by admin";
            securityService.blockIPManually(ipAddress, blockReason, hoursToBlock);

            Map<String, String> response = new HashMap<>();
            response.put("message", "IP address " + ipAddress + " has been blocked for " + hoursToBlock + " hours");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Session Management Endpoints

    @GetMapping("/sessions/{username}")
    public ResponseEntity<?> getUserSessions(@PathVariable String username) {
        try {
            List<UserSessionDto> sessions = securityService.getActiveSessions(username).stream()
                    .map(UserSessionDto::new).collect(Collectors.toList());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/sessions/deactivate/{sessionId}")
    public ResponseEntity<Map<String, String>> deactivateSession(@PathVariable String sessionId) {
        try {
            securityService.deactivateSession(sessionId);
            refreshTokenService.deleteBySessionId(sessionId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Session " + sessionId + " has been deactivated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/sessions/deactivate-all/{username}")
    public ResponseEntity<Map<String, String>> deactivateAllUserSessions(@PathVariable String username) {
        try {
            securityService.deactivateAllUserSessions(username);
            Map<String, String> response = new HashMap<>();
            response.put("message", "All sessions for user " + username + " have been deactivated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/sessions/cleanup-expired")
    public ResponseEntity<Map<String, String>> cleanupExpiredSessions() {
        try {
            securityService.cleanupExpiredSessions();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Expired sessions have been cleaned up");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/users/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> changeUserPassword(@PathVariable Long userId,
            @Valid @RequestBody AdminChangePasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "New password and confirm password do not match");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found with ID: " + userId);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        User user = userOpt.get();

        // Encode and set new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate active sessions & refresh tokens for security
        try {
            securityService.deactivateAllUserSessions(user.getUsername());
            refreshTokenService.deleteByUsername(user.getUsername());
        } catch (Exception e) {
            ErrorLog.debug(e);
            // Non-blocking fallback log
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password updated successfully for user " + user.getUsername());
        response.put("username", user.getUsername());
        response.put("userId", userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Moves medical files uploaded before the private bucket existed into it. Dry run by default;
     * public copies are kept unless deleteOriginals=true (run that only after checking the moved files open).
     */
    @PostMapping("/storage/move-medical-files")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> moveMedicalFilesToPrivateBucket(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean deleteOriginals) {
        return ResponseEntity.ok(medicalFileMigrationService.moveMedicalFilesToPrivateBucket(dryRun, deleteOriginals));
    }

    /**
     * Rewrites encrypted patient-data columns into AES-256-GCM (old AES/ECB values and plaintext). Dry run by
     * default. Take a database backup (Neon branch) before the real run.
     */
    @PostMapping("/security/reencrypt-fields")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reencryptFields(@RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(fieldReencryptionService.reencryptAll(dryRun));
    }

    /** Audit trail of patient-data access, newest first. Filters are optional; dates are ISO date-times. */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<com.vikrant.careSync.entity.AuditLog>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String recordId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), Math.min(Math.max(1, size), 200));
        return ResponseEntity.ok(auditService.search(username, recordId, from, to, pageable));
    }
}
