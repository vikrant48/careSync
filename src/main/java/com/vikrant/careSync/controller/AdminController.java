package com.vikrant.careSync.controller;

import com.vikrant.careSync.security.entity.BlockedIP;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.dto.BlockedIPDto;
import com.vikrant.careSync.dto.UserSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.dto.UserSummaryDto;
import com.vikrant.careSync.dto.DoctorDto;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AdminController {

    private final SecurityService securityService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> getAllUsersSummary() {
        List<UserSummaryDto> users = userRepository.findAll().stream()
                .map(UserSummaryDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorDto>> getAllDoctors() {
        List<DoctorDto> doctors = doctorRepository.findAll().stream()
                .map(DoctorDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(doctors);
    }

    @PutMapping("/users/{username}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleUserActiveStatus(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found with username: " + username);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        User user = userOpt.get();
        boolean newStatus = !Boolean.TRUE.equals(user.getIsActive());
        user.setIsActive(newStatus);
        userRepository.save(user);

        // Sync with linked Doctor or Patient if present
        doctorRepository.findById(user.getId()).ifPresent(doctor -> {
            doctor.setIsActive(newStatus);
            doctorRepository.save(doctor);
        });
        patientRepository.findById(user.getId()).ifPresent(patient -> {
            patient.setIsActive(newStatus);
            patientRepository.save(patient);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User " + username + " is now " + (newStatus ? "ACTIVE" : "INACTIVE"));
        response.put("username", username);
        response.put("isActive", newStatus);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{username}/status")
    public ResponseEntity<Map<String, Object>> setUserActiveStatus(
            @PathVariable String username,
            @RequestParam("active") boolean active) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found with username: " + username);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        User user = userOpt.get();
        user.setIsActive(active);
        userRepository.save(user);

        doctorRepository.findById(user.getId()).ifPresent(doctor -> {
            doctor.setIsActive(active);
            doctorRepository.save(doctor);
        });
        patientRepository.findById(user.getId()).ifPresent(patient -> {
            patient.setIsActive(active);
            patientRepository.save(patient);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User " + username + " active status set to " + active);
        response.put("username", username);
        response.put("isActive", active);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/doctors/{doctorId}/verify")
    public ResponseEntity<Map<String, Object>> verifyDoctor(
            @PathVariable Long doctorId,
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
            List<BlockedIPDto> blockedIPs = securityService.getAllBlockedIPs().stream()
                    .map(BlockedIPDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(blockedIPs);
        } catch (Exception e) {
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/block-doctor/{doctorId}")
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
            doctor.setIsActive(false);
            doctorRepository.save(doctor);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Doctor " + doctor.getUsername() + " has been blocked");
            response.put("reason", reason != null ? reason : "No reason provided");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/block-patient/{patientId}")
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
            patient.setIsActive(false);
            patientRepository.save(patient);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Patient " + patient.getUsername() + " has been blocked");
            response.put("reason", reason != null ? reason : "No reason provided");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/unblock-doctor/{doctorId}")
    public ResponseEntity<Map<String, String>> unblockDoctor(@PathVariable Long doctorId) {
        try {
            Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
            if (doctorOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Doctor not found with ID: " + doctorId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Doctor doctor = doctorOpt.get();
            doctor.setIsActive(true);
            doctorRepository.save(doctor);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Doctor " + doctor.getUsername() + " has been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/unblock-patient/{patientId}")
    public ResponseEntity<Map<String, String>> unblockPatient(@PathVariable Long patientId) {
        try {
            Optional<Patient> patientOpt = patientRepository.findById(patientId);
            if (patientOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Patient not found with ID: " + patientId);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Patient patient = patientOpt.get();
            patient.setIsActive(true);
            patientRepository.save(patient);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Patient " + patient.getUsername() + " has been unblocked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Session Management Endpoints

    @GetMapping("/sessions/{username}")
    public ResponseEntity<?> getUserSessions(@PathVariable String username) {
        try {
            List<UserSessionDto> sessions = securityService.getActiveSessions(username).stream()
                    .map(UserSessionDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/sessions/deactivate/{sessionId}")
    public ResponseEntity<Map<String, String>> deactivateSession(@PathVariable String sessionId) {
        try {
            securityService.deactivateSession(sessionId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Session " + sessionId + " has been deactivated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}