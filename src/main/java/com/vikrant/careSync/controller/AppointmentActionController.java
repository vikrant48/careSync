package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AllowedActionDto;
import com.vikrant.careSync.dto.AppointmentResponse;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.AppointmentStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Appointment Actions", description = "Unified 7-stage state machine action execution endpoints")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class AppointmentActionController {

    private final AppointmentStateMachineService stateMachineService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            boolean isDoctor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            boolean isPatient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

            if (isDoctor) {
                Doctor doctor = doctorRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("Doctor not found: " + username));
                return doctor.getUser();
            } else if (isPatient) {
                Patient patient = patientRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("Patient not found: " + username));
                return patient.getUser();
            } else {
                User user = new User();
                user.setUsername(username);
                user.setRole(User.Role.ADMIN);
                return user;
            }
        }
        throw new RuntimeException("User not authenticated");
    }

    @GetMapping("/{id}/allowed-actions")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getAllowedActions(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            List<AllowedActionDto> actions = stateMachineService.getAllowedActions(id, currentUser);
            return ResponseEntity.ok(actions);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/{id}/actions/{action}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> executeAction(
            @PathVariable Long id,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            User currentUser = getCurrentUser();
            Appointment updated = stateMachineService.executeAction(id, action, currentUser, payload);
            return ResponseEntity.ok(new AppointmentResponse(updated));
        } catch (IllegalStateException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(409).body(errorResponse); // 409 Conflict for invalid state transitions
        } catch (SecurityException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(403).body(errorResponse); // 403 Forbidden for unauthorized role/ownership
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
