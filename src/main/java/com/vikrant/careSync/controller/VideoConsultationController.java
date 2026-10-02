package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.LiveKitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@Slf4j
@Tag(name = "Video Consultation", description = "Endpoints for LiveKit video room tokens, waiting status, and room lifecycle")
public class VideoConsultationController {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;
    private final LiveKitService liveKitService;

    /**
     * Issue short-lived LiveKit token for authenticated doctor or patient.
     * Enforces time window [start - 10 min, end + 15 min].
     */
    @Operation(summary = "Get LiveKit video room token", description = "Validates user permissions and time window before returning a JWT token for LiveKit consultation")
    @PostMapping("/{id}/video/token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getVideoToken(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + id));

            // Validate caller identity & determine role
            boolean isDoctor = false;
            boolean isPatient = false;

            Optional<Doctor> docOpt = doctorRepository.findByUsername(username);
            if (docOpt.isPresent() && docOpt.get().getId().equals(appointment.getDoctor().getId())) {
                isDoctor = true;
            }

            Optional<Patient> patOpt = patientRepository.findByUsername(username);
            if (patOpt.isPresent() && patOpt.get().getId().equals(appointment.getPatient().getId())) {
                isPatient = true;
            }

            if (!isDoctor && !isPatient) {
                Map<String, String> err = new HashMap<>();
                err.put("error", "Access denied: You are not authorized to join this consultation.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
            }

            // Time window check: [start - 10 min, end + 45 min]
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startWindow = appointment.getAppointmentDateTime().minusMinutes(10);
            LocalDateTime endWindow = appointment.getAppointmentDateTime().plusMinutes(45); // 30m appt + 15m buffer

            java.time.format.DateTimeFormatter apptFormatter = java.time.format.DateTimeFormatter
                    .ofPattern("MMM dd, yyyy 'at' hh:mm a");
            java.time.format.DateTimeFormatter timeOnlyFormatter = java.time.format.DateTimeFormatter
                    .ofPattern("hh:mm a");

            String scheduledTimeStr = appointment.getAppointmentDateTime().format(apptFormatter);
            String openTimeStr = startWindow.format(timeOnlyFormatter);

            if (now.isBefore(startWindow)) {
                Map<String, String> err = new HashMap<>();
                err.put("error",
                        "Your appointment is scheduled for " + scheduledTimeStr
                                + ". The consultation room will open 10 minutes prior (at " + openTimeStr
                                + "). Please join after " + openTimeStr + ".");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
            }

            if (now.isAfter(endWindow)) {
                Map<String, String> err = new HashMap<>();
                err.put("error", "The consultation time window for your appointment scheduled for " + scheduledTimeStr
                        + " has expired.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
            }

            // Ensure unguessable room name appt-<id>-<uuid>
            String roomName = appointment.getVideoRoomId();
            if (roomName == null || !roomName.startsWith("appt-")) {
                roomName = "appt-" + appointment.getId() + "-" + UUID.randomUUID().toString();
                appointment.setVideoRoomId(roomName);
                appointmentRepository.save(appointment);
            }

            String roleStr = isDoctor ? "DOCTOR" : "PATIENT";
            String displayName = isDoctor ? "Dr. " + appointment.getDoctor().getName()
                    : appointment.getPatient().getName();
            String userId = username;

            long ttlSeconds = Duration.between(now, endWindow).getSeconds();
            String token = liveKitService.generateToken(roomName, userId, displayName, roleStr, ttlSeconds);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("url", liveKitService.getLivekitUrl());
            response.put("roomName", roomName);
            response.put("role", roleStr);
            response.put("displayName", displayName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating video token for appointment {}: {}", id, e.getMessage(), e);
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Check if doctor is in room (for patient waiting screen polling).
     */
    @Operation(summary = "Check doctor presence in video room", description = "Allows patient waiting screen to poll doctor room status")
    @GetMapping("/{id}/video/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getVideoStatus(@PathVariable Long id) {
        try {
            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + id));

            String roomName = appointment.getVideoRoomId();
            boolean doctorInRoom = false;

            if (roomName != null) {
                doctorInRoom = liveKitService.isDoctorInRoom(roomName);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("appointmentId", id);
            resp.put("roomName", roomName);
            resp.put("doctorInRoom", doctorInRoom);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Terminate the room.
     */
    @Operation(summary = "End video consultation", description = "Closes the LiveKit room and completes the appointment (Doctor only)")
    @PostMapping("/{id}/video/end")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> endVideoConsultation(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Doctor doctor = doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!appointment.getDoctor().getId().equals(doctor.getId())) {
                Map<String, String> err = new HashMap<>();
                err.put("error", "Access denied: Only the assigned doctor can end this session.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
            }

            if (appointment.getVideoRoomId() != null) {
                liveKitService.endRoom(appointment.getVideoRoomId());
            }

            // Keep appointment status as IN_PROGRESS after ending video room session
            appointmentService.updateAppointmentStatus(id, Appointment.Status.IN_PROGRESS, doctor.getUser());

            Map<String, String> resp = new HashMap<>();
            resp.put("message", "Consultation session ended. Appointment status remains IN_PROGRESS.");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }
}
