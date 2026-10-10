package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.AppointmentFollowThroughService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
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
    private final AppointmentFollowThroughService appointmentFollowThroughService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final FeedbackRepository feedbackRepository;

    private AppointmentResponse mapToResponse(Appointment a) {
        AppointmentResponse resp = new AppointmentResponse(a);
        if (a != null && a.getId() != null) {
            resp.setFeedbackSubmitted(feedbackRepository.findByAppointmentId(a.getId()).isPresent());
        }
        return resp;
    }

    // Get current authenticated user (for patient endpoints)
    private Patient getCurrentPatient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    // Get current authenticated user (for doctor endpoints)
    private Doctor getCurrentDoctor() {
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
            if (request.appointmentDateTime.isBefore(LocalDateTime.now())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can't book an appointment in the past. Please select a future date and time.");
                return ResponseEntity.badRequest().body(error);
            }

            Patient currentUser = getCurrentPatient();
            log.info("Current user: {}", currentUser.getId());
            log.info("Doctor ID: {}", request.doctorId);
            log.info("Appointment time: {}", request.appointmentDateTime);

            // Use the bookAppointment method from service (patientId is automatically set
            // to current user)
            Appointment created = appointmentService.bookAppointment(
                    request.doctorId,
                    currentUser.getId(),
                    request.appointmentDateTime,
                    request.reason,
                    request.bookingSource);

            log.info("Appointment created with ID: {}", created.getId());

            return ResponseEntity.ok(new AppointmentResponse(created));
        } catch (Exception e) {
            log.error("=== ERROR in appointment creation: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/patient/hold-slot")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> holdSlot(@Valid @RequestBody HoldSlotRequest request) {
        try {
            Patient currentUser = getCurrentPatient();
            boolean success = appointmentService.holdSlot(request.doctorId, request.appointmentDateTime,
                    currentUser.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            if (!success) {
                response.put("message", "Slot is already locked or unavailable");
                return ResponseEntity.badRequest().body(response);
            }
            response.put("message", "Slot held for 5 minutes");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/patient/release-slot")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> releaseSlot(@Valid @RequestBody HoldSlotRequest request) {
        try {
            appointmentService.releaseSlotHold(request.doctorId, request.appointmentDateTime);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Slot lock released");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @lombok.Data
    public static class HoldSlotRequest {
        public Long doctorId;
        public LocalDateTime appointmentDateTime;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Book appointment with payment", description = "Creates appointment and processes payment atomically")
    @PostMapping("/patient/book-with-payment")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookAppointmentWithPayment(
            @Valid @RequestBody BookAppointmentWithPaymentRequest request) {
        try {
            log.info("=== Starting atomic appointment creation with payment ===");
            Patient patient = getCurrentPatient();
            AppointmentResponse response = appointmentService.bookAppointmentWithPayment(patient.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("=== ERROR in atomic appointment creation with payment: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/patient/{id}/intake")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> submitPreVisitIntake(
            @PathVariable Long id,
            @Valid @RequestBody com.vikrant.careSync.dto.PreVisitIntakeRequest request) {
        try {
            Patient patient = getCurrentPatient();
            com.vikrant.careSync.dto.PreVisitIntakeResponse response = appointmentService.submitPreVisitIntake(id,
                    request, patient.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/follow-through")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getFollowThrough(@PathVariable Long id) {
        try {
            Patient patient = getCurrentPatient();
            return ResponseEntity.ok(appointmentFollowThroughService.getFollowThrough(id, patient.getId()));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/intake-summary")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'PATIENT')")
    public ResponseEntity<?> getPreVisitIntakeSummary(@PathVariable Long id) {
        try {
            com.vikrant.careSync.dto.PreVisitIntakeResponse response = appointmentService.getPreVisitIntakeSummary(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/pre-visit-brief")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getPreVisitBrief(@PathVariable Long id) {
        try {
            Doctor currentDoctor = getCurrentDoctor();
            return ResponseEntity.ok(appointmentService.getPreVisitBrief(id, currentDoctor.getId()));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/generate-soap-draft")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<?> generateSoapDraft(
            @PathVariable Long id,
            @RequestBody(required = false) GenerateSoapDraftRequest request) {
        try {
            String liveTranscript = (request != null) ? request.getLiveTranscript() : "";
            com.vikrant.careSync.dto.SoapReportDto response = appointmentService.generateAndSaveSoapDraft(id,
                    liveTranscript);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/soap-draft")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'PATIENT')")
    public ResponseEntity<?> getSoapDraft(@PathVariable Long id) {
        try {
            com.vikrant.careSync.dto.SoapReportDto response = appointmentService.getSoapDraft(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/soap-draft")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> saveReviewedSoapDraft(
            @PathVariable Long id,
            @RequestBody com.vikrant.careSync.dto.SoapReportDto reportDto) {
        try {
            Doctor currentDoctor = getCurrentDoctor();
            return ResponseEntity.ok(appointmentService.saveReviewedSoapDraft(id, reportDto, currentDoctor.getId()));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/sign-report")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> signReport(
            @PathVariable Long id,
            @Valid @RequestBody com.vikrant.careSync.dto.SoapReportDto reportDto) {
        try {
            Doctor currentDoctor = getCurrentDoctor();
            com.vikrant.careSync.dto.SoapReportDto signedReport = appointmentService.signMedicalReport(id, reportDto,
                    currentDoctor.getId());
            return ResponseEntity.ok(signedReport);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/admin/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAppointmentAnalytics(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long doctorId) {
        try {
            com.vikrant.careSync.dto.AppointmentAnalyticsResponse response = appointmentService
                    .getAppointmentAnalytics(startDate, endDate, doctorId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/audit-trail")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<?> getAppointmentAuditTrail(@PathVariable Long id) {
        try {
            List<com.vikrant.careSync.dto.AppointmentStatusLogDto> auditTrail = appointmentService
                    .getAppointmentAuditTrail(id);
            return ResponseEntity.ok(auditTrail);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @lombok.Data
    public static class GenerateSoapDraftRequest {
        private String liveTranscript;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get my appointments", description = "Retrieves a list of all appointments for the authenticated patient")
    @GetMapping("/patient/my-appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyAppointments() {
        try {
            Patient currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByPatient(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(this::mapToResponse)
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
            Patient currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByPatient(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(this::mapToResponse)
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
            Patient currentUser = getCurrentPatient();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    appointmentStatus);
            List<AppointmentResponse> responses = appointments.stream()
                    .map(this::mapToResponse)
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
            Patient currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.COMPLETED);
            List<AppointmentResponse> responses = appointments.stream()
                    .map(this::mapToResponse)
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
            Patient currentUser = getCurrentPatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.CANCELLED);
            List<AppointmentResponse> responses = appointments.stream()
                    .map(this::mapToResponse)
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
            Patient currentUser = getCurrentPatient();

            // Create updated appointment object
            Appointment updatedAppointment = new Appointment();
            updatedAppointment.setDoctor(doctorRepository.findById(request.doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found")));
            updatedAppointment.setAppointmentDateTime(request.appointmentDateTime);
            updatedAppointment.setReason(request.reason);

            // Use the updated service method
            Appointment updated = appointmentService.updateAppointment(id, updatedAppointment, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updated));
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
            Patient currentUser = getCurrentPatient();

            // Use the updated service method
            appointmentService.cancelAppointment(id, currentUser.getUser());
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
            Patient currentUser = getCurrentPatient();

            // Parse the new date time
            LocalDateTime newAppointmentDateTime = LocalDateTime.parse(newDateTime);

            // Check if new appointment date is in the past
            if (newAppointmentDateTime.isBefore(LocalDateTime.now())) {
                Map<String, String> error = new HashMap<>();
                error.put("error",
                        "You can't reschedule an appointment to the past. Please select a future date and time.");
                return ResponseEntity.badRequest().body(error);
            }

            // Use the reschedule service method
            Appointment rescheduled = appointmentService.rescheduleAppointment(id, newAppointmentDateTime,
                    currentUser.getUser());

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("message", "Appointment rescheduled successfully");
            successResponse.put("appointment", new AppointmentResponse(rescheduled));
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // DOCTOR ENDPOINTS - Only accessible by doctors

    @GetMapping("/doctor/dashboard-metrics")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getDoctorDashboardMetrics() {
        try {
            Doctor currentUser = getCurrentDoctor();
            DoctorDashboardMetricsResponse metrics = appointmentService.getDoctorDashboardMetrics(currentUser.getId());
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatients() {
        try {
            Doctor currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/paginated")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatientsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "UPCOMING") String range,
            @RequestParam(required = false) String search) {
        try {
            Doctor currentUser = getCurrentDoctor();
            List<AppointmentResponse> responses = appointmentService.getDoctorAppointmentsPaginated(
                    currentUser.getId(), page, size, status, range, search);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/my-patients/count")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatientsCount(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "UPCOMING") String range,
            @RequestParam(required = false) String search) {
        try {
            Doctor currentUser = getCurrentDoctor();
            long count = appointmentService.countDoctorAppointments(currentUser.getId(), status, range, search);
            return ResponseEntity.ok(count);
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
            Doctor currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByDoctor(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
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
            Doctor currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getTodayAppointments(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
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
            Doctor currentUser = getCurrentDoctor();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatus(currentUser.getId(),
                    appointmentStatus);
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
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
            Doctor currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getCompletedAppointments(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
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
            Doctor currentUser = getCurrentDoctor();
            List<Appointment> appointments = appointmentService.getCancelledAppointments(currentUser.getId());
            List<AppointmentResponse> responses = appointments.stream()
                    .map(AppointmentResponse::new)
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
            Doctor currentUser = getCurrentDoctor();
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
            Doctor currentUser = getCurrentDoctor();

            // Convert string status to enum
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());

            // Use the updated service method
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id, appointmentStatus,
                    currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> updateAppointmentStatusAnyRole(@PathVariable Long id, @RequestParam String status) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());

            boolean isDoctor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            boolean isPatient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

            com.vikrant.careSync.entity.User userEntity = null;
            if (isDoctor) {
                userEntity = getCurrentDoctor().getUser();
            } else if (isPatient) {
                userEntity = getCurrentPatient().getUser();
            }

            if (userEntity == null) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Unauthorized user role");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id, appointmentStatus,
                    userEntity);
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
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
            Doctor currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CONFIRMED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
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
            Doctor currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.COMPLETED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
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
            Doctor currentUser = getCurrentDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CANCELLED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getAppointmentById(@PathVariable Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Appointment appointment = appointmentService.getAppointmentById(id);

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isDoctor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            boolean isPatient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

            if (!isAdmin) {
                if (isDoctor) {
                    Doctor doctor = getCurrentDoctor();
                    if (!appointment.getDoctor().getId().equals(doctor.getId())) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Access denied: Appointment belongs to another doctor");
                        return ResponseEntity.status(403).body(error);
                    }
                } else if (isPatient) {
                    Patient patient = getCurrentPatient();
                    if (!appointment.getPatient().getId().equals(patient.getId())) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Access denied: Appointment belongs to another patient");
                        return ResponseEntity.status(403).body(error);
                    }
                }
            }

            return ResponseEntity.ok(new AppointmentResponse(appointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getAppointmentDetails(@PathVariable Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            AppointmentDetailsResponse details = appointmentService.getAppointmentDetails(id);

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isDoctor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            boolean isPatient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

            if (!isAdmin) {
                if (isDoctor && !details.getDoctorId().equals(getCurrentDoctor().getId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Access denied: Appointment belongs to another doctor"));
                }
                if (isPatient && !details.getPatientId().equals(getCurrentPatient().getId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Access denied: Appointment belongs to another patient"));
                }
            }

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAppointmentByIdForAdmin(@PathVariable Long id) {
        return getAppointmentById(id);
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
            SlotAvailabilityResponse response = appointmentService.getAvailableSlots(doctorId,
                    date);
            return ResponseEntity.ok(response);
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
            Patient currentUser = getCurrentPatient();

            // Book emergency appointment at current time
            Appointment emergencyAppointment = appointmentService.bookEmergencyAppointment(
                    doctorId,
                    currentUser.getId(),
                    reason != null ? reason : "Emergency appointment");

            return ResponseEntity.ok(new AppointmentResponse(emergencyAppointment));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}/completion-preview")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<?> getCompletionPreview(@PathVariable Long id) {
        try {
            CompletionPreviewDto preview = appointmentService.getCompletionPreview(id);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/{id}/esign-and-complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> eSignAndCompleteAppointment(
            @PathVariable Long id,
            @Valid @RequestBody ESignCompletionRequest request) {
        try {
            Doctor currentDoctor = getCurrentDoctor();
            ESignCompletionResponse response = appointmentService.eSignAndCompleteAppointment(id, request,
                    currentDoctor.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}