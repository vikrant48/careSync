package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AppointmentResponse;
import com.vikrant.careSync.dto.CreateAppointmentRequest;
import com.vikrant.careSync.dto.DoctorAppointmentResponse;
import com.vikrant.careSync.dto.PatientAppointmentResponse;
import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Appointments", description = "Endpoints for booking, managing, and tracking medical appointments")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    // Get current authenticated user (for patient endpoints)
    private User getCurrentPatient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    // Get current authenticated user (for doctor endpoints)
    private User getCurrentDoctor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    // PATIENT ENDPOINTS - Only accessible by patients

    @io.swagger.v3.oas.annotations.Operation(summary = "Book an appointment", description = "Creates a new appointment booking for a patient")
    @PostMapping("/patient/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        try {
            log.info("=== Starting appointment creation ===");

            // Check if appointment date is in the past
            if (request.appointmentDateTime.isBefore(java.time.LocalDateTime.now())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can't book an appointment in the past. Please select a future date and time.");
                return ResponseEntity.badRequest().body(error);
            }

            User currentUser = getCurrentPatient();
            log.info("Current user: {}", currentUser.getId());
            log.info("Doctor ID: {}", request.doctorId);
            log.info("Appointment time: {}", request.appointmentDateTime);

            // Use the bookAppointment method from service (patientId is automatically set
            // to current user)
            Appointment created = appointmentService.bookAppointment(
                    request.doctorId,
                    currentUser.getId(),
                    request.appointmentDateTime,
                    request.reason);

            log.info("Appointment created with ID: {}", created.getId());

            return ResponseEntity.ok(new PatientAppointmentResponse(created));
        } catch (Exception e) {
            log.error("=== ERROR in appointment creation: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get my appointments", description = "Retrieves a list of all appointments for the authenticated patient")
    @GetMapping("/patient/my-appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyAppointments() {
        try {
            User currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByPatient(currentUser.getId());
            List<PatientAppointmentResponse> responses = appointments.stream()
                    .map(PatientAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/my-appointments/upcoming")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyUpcomingAppointments() {
        try {
            User currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByPatient(currentUser.getId());
            List<PatientAppointmentResponse> responses = appointments.stream()
                    .map(PatientAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/my-appointments/status/{status}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyAppointmentsByStatus(@PathVariable String status) {
        try {
            User currentUser = getCurrentPatient();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    appointmentStatus);
            List<PatientAppointmentResponse> responses = appointments.stream()
                    .map(PatientAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/my-appointments/completed")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyCompletedAppointments() {
        try {
            User currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.COMPLETED);
            List<PatientAppointmentResponse> responses = appointments.stream()
                    .map(PatientAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/my-appointments/cancelled")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyCancelledAppointments() {
        try {
            User currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.CANCELLED);
            List<PatientAppointmentResponse> responses = appointments.stream()
                    .map(PatientAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/patient/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> updateMyAppointment(@PathVariable Long id,
            @Valid @RequestBody CreateAppointmentRequest request) {
        try {
            User currentUser = getCurrentPatient();

            // Create updated appointment object
            Appointment updatedAppointment = new Appointment();
            updatedAppointment.setDoctor(doctorRepository.findById(request.doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found")));
            updatedAppointment.setAppointmentDateTime(request.appointmentDateTime);
            updatedAppointment.setReason(request.reason);

            // Use the updated service method
            Appointment updated = appointmentService.updateAppointment(id, updatedAppointment, currentUser);
            return ResponseEntity.ok(new PatientAppointmentResponse(updated));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/patient/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> cancelMyAppointment(@PathVariable Long id) {
        try {
            User currentUser = getCurrentPatient();

            // Use the updated service method
            appointmentService.cancelAppointment(id, currentUser);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Appointment cancelled successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/patient/{id}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> rescheduleMyAppointment(@PathVariable Long id, @RequestParam String newDateTime) {
        try {
            User currentUser = getCurrentPatient();

            // Parse the new date time
            java.time.LocalDateTime newAppointmentDateTime = java.time.LocalDateTime.parse(newDateTime);

            // Check if new appointment date is in the past
            if (newAppointmentDateTime.isBefore(java.time.LocalDateTime.now())) {
                Map<String, String> error = new HashMap<>();
                error.put("error",
                        "You can't reschedule an appointment to the past. Please select a future date and time.");
                return ResponseEntity.badRequest().body(error);
            }

            // Use the reschedule service method
            Appointment rescheduled = appointmentService.rescheduleAppointment(id, newAppointmentDateTime, currentUser);

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("message", "Appointment rescheduled successfully");
            successResponse.put("appointment", new PatientAppointmentResponse(rescheduled));
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // DOCTOR ENDPOINTS - Only accessible by doctors

    @GetMapping("/doctor/my-patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(currentUser.getId());
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/upcoming")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyUpcomingPatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByDoctor(currentUser.getId());
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/today")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyTodayPatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getTodayAppointments(currentUser.getId());
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/status/{status}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatientsByStatus(@PathVariable String status) {
        try {
            User currentUser = getCurrentDoctor();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatus(currentUser.getId(),
                    appointmentStatus);
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/completed")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyCompletedPatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getCompletedAppointments(currentUser.getId());
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/cancelled")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyCancelledPatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getCancelledAppointments(currentUser.getId());
            List<DoctorAppointmentResponse> responses = appointments.stream()
                    .map(DoctorAppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/unique-patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getUniquePatients() {
        try {
            User currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(currentUser.getId());

            // Get unique patients from appointments and map to UserDto
            List<UserDto> uniquePatients = appointments.stream()
                    .map(Appointment::getPatient)
                    .distinct()
                    .map(UserDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(uniquePatients);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Doctor can change appointment status (CONFIRM, COMPLETE, CANCEL)
    @PutMapping("/doctor/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> updateAppointmentStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            User currentUser = getCurrentDoctor();

            // Convert string status to enum
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());

            // Use the updated service method
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id, appointmentStatus,
                    currentUser);
            return ResponseEntity.ok(new DoctorAppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Doctor can confirm appointment
    @PutMapping("/doctor/{id}/confirm")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> confirmAppointment(@PathVariable Long id) {
        try {
            User currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CONFIRMED, currentUser);
            return ResponseEntity.ok(new DoctorAppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Doctor can complete appointment
    @PutMapping("/doctor/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id) {
        try {
            User currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.COMPLETED, currentUser);
            return ResponseEntity.ok(new DoctorAppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Doctor can cancel appointment
    @PutMapping("/doctor/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> cancelAppointmentByDoctor(@PathVariable Long id) {
        try {
            User currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CANCELLED, currentUser);
            return ResponseEntity.ok(new DoctorAppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ADMIN ENDPOINTS - Only accessible by admins

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAppointmentById(@PathVariable Long id) {
        try {
            Appointment appointment = appointmentService.getAppointmentById(id);
            return ResponseEntity.ok(new AppointmentResponse(appointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAppointment(@PathVariable Long id) {
        try {
            appointmentService.deleteAppointment(id);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Appointment deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // PUBLIC ENDPOINTS - Accessible by authenticated users

    @GetMapping("/available-slots")
    public ResponseEntity<?> getAvailableSlots(@RequestParam Long doctorId, @RequestParam String date) {
        try {
            List<String> availableSlots = appointmentService.getAvailableSlots(doctorId, date);
            return ResponseEntity.ok(availableSlots);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // EMERGENCY BOOKING - Immediate appointment booking
    @PostMapping("/patient/book-emergency")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookEmergencyAppointment(@RequestParam Long doctorId,
            @RequestParam(required = false) String reason) {
        try {
            User currentUser = getCurrentPatient();

            // Book emergency appointment at current time
            Appointment emergencyAppointment = appointmentService.bookEmergencyAppointment(
                    doctorId,
                    currentUser.getId(),
                    reason != null ? reason : "Emergency appointment");

            return ResponseEntity.ok(new PatientAppointmentResponse(emergencyAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}