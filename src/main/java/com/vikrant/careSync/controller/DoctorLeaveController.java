package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.DoctorLeave;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.service.DoctorLeaveService;
import com.vikrant.careSync.dto.DoctorLeaveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctor-leaves")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class DoctorLeaveController {

    private final DoctorLeaveService doctorLeaveService;
    private final DoctorRepository doctorRepository;

    private User getCurrentDoctor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> addLeave(@RequestBody Map<String, String> request) {
        try {
            User currentDoctor = getCurrentDoctor();
            LocalDate startDate = LocalDate.parse(request.get("startDate"));
            LocalDate endDate = LocalDate.parse(request.get("endDate"));
            String reason = request.get("reason");

            DoctorLeave leave = doctorLeaveService.addLeave(currentDoctor.getId(), startDate, endDate, reason);
            return ResponseEntity.ok(new DoctorLeaveDto(leave));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/my-leaves")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyLeaves() {
        try {
            User currentDoctor = getCurrentDoctor();
            List<DoctorLeaveDto> leaves = doctorLeaveService.getDoctorLeaves(currentDoctor.getId()).stream()
                    .map(DoctorLeaveDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(leaves);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getUpcomingLeaves() {
        try {
            User currentDoctor = getCurrentDoctor();
            List<DoctorLeaveDto> leaves = doctorLeaveService.getUpcomingLeaves(currentDoctor.getId()).stream()
                    .map(DoctorLeaveDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(leaves);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteLeave(@PathVariable Long id) {
        try {
            User currentDoctor = getCurrentDoctor();
            doctorLeaveService.deleteLeave(id, currentDoctor.getId());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Leave record deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
