package com.vikrant.careSync.controller;

import com.vikrant.careSync.security.entity.BlockedIP;
import com.vikrant.careSync.security.entity.UserSession;
import com.vikrant.careSync.security.service.SecurityService;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final SecurityService securityService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @GetMapping("/blocked-ips")
    public ResponseEntity<List<BlockedIP>> getAllBlockedIPs() {
        try {
            List<BlockedIP> blockedIPs = securityService.getAllBlockedIPs();
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
    public ResponseEntity<Map<String, String>> blockDoctor(@PathVariable Long doctorId, @RequestParam(required = false) String reason) {
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
    public ResponseEntity<Map<String, String>> blockPatient(@PathVariable Long patientId, @RequestParam(required = false) String reason) {
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
    public ResponseEntity<Map<String, String>> blockIP(@RequestParam String ipAddress, @RequestParam(required = false) String reason, @RequestParam(required = false, defaultValue = "24") int hoursToBlock) {
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
            List<UserSession> sessions = securityService.getActiveSessions(username);
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