package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.exception.GlobalExceptionHandler;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
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

import java.time.LocalDate;
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
    private final CurrentUserService currentUserService;
    private final FeedbackRepository feedbackRepository;

    /** List version of mapToResponse: one feedback query for the whole list instead of one per row. */
    private List<AppointmentResponse> mapToResponses(List<Appointment> appointments) {
        java.util.Set<Long> withFeedback = appointments.isEmpty() ? java.util.Set.of()
                : new java.util.HashSet<>(feedbackRepository.findAppointmentIdsWithFeedback(
                        appointments.stream().map(Appointment::getId).toList()));
        return appointments.stream().map(a -> {
            AppointmentResponse resp = new AppointmentResponse(a);
            resp.setFeedbackSubmitted(withFeedback.contains(a.getId()));
            return resp;
        }).collect(Collectors.toList());
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

            Patient currentUser = currentUserService.requirePatient();
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/patient/hold-slot")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> holdSlot(@Valid @RequestBody HoldSlotRequest request) {
        try {
            Patient currentUser = currentUserService.requirePatient();
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/patient/release-slot")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> releaseSlot(@Valid @RequestBody HoldSlotRequest request) {
        try {
            Patient currentUser = currentUserService.requirePatient();
            if (!appointmentService.releaseSlotHold(request.doctorId, request.appointmentDateTime,
                    currentUser.getId())) {
                return ResponseEntity.status(403).body(GlobalExceptionHandler.forbiddenBody());
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Slot lock released");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
            Patient patient = currentUserService.requirePatient();
            AppointmentResponse response = appointmentService.bookAppointmentWithPayment(patient.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("=== ERROR in atomic appointment creation with payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/patient/{id}/intake")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> submitPreVisitIntake(
            @PathVariable Long id,
            @Valid @RequestBody com.vikrant.careSync.dto.PreVisitIntakeRequest request) {
        try {
            Patient patient = currentUserService.requirePatient();
            com.vikrant.careSync.dto.PreVisitIntakeResponse response = appointmentService.submitPreVisitIntake(id,
                    request, patient.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/follow-through")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getFollowThrough(@PathVariable Long id) {
        try {
            Patient patient = currentUserService.requirePatient();
            return ResponseEntity.ok(appointmentFollowThroughService.getFollowThrough(id, patient.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/intake-summary")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'PATIENT') and @access.canAccessAppointment(#id)")
    public ResponseEntity<?> getPreVisitIntakeSummary(@PathVariable Long id) {
        try {
            com.vikrant.careSync.dto.PreVisitIntakeResponse response = appointmentService.getPreVisitIntakeSummary(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/pre-visit-brief")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getPreVisitBrief(@PathVariable Long id) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            return ResponseEntity.ok(appointmentService.getPreVisitBrief(id, currentDoctor.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/{id}/generate-soap-draft")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN') and @access.isAppointmentDoctor(#id)")
    public ResponseEntity<?> generateSoapDraft(
            @PathVariable Long id,
            @RequestBody(required = false) GenerateSoapDraftRequest request) {
        try {
            String liveTranscript = (request != null) ? request.getLiveTranscript() : "";
            com.vikrant.careSync.dto.SoapReportDto response = appointmentService.generateAndSaveSoapDraft(id,
                    liveTranscript);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/soap-draft")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'PATIENT') and @access.canAccessAppointment(#id)")
    public ResponseEntity<?> getSoapDraft(@PathVariable Long id) {
        try {
            com.vikrant.careSync.dto.SoapReportDto response = appointmentService.getSoapDraft(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/{id}/soap-draft")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> saveReviewedSoapDraft(
            @PathVariable Long id,
            @RequestBody com.vikrant.careSync.dto.SoapReportDto reportDto) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            return ResponseEntity.ok(appointmentService.saveReviewedSoapDraft(id, reportDto, currentDoctor.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/{id}/sign-report")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> signReport(
            @PathVariable Long id,
            @Valid @RequestBody com.vikrant.careSync.dto.SoapReportDto reportDto) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            com.vikrant.careSync.dto.SoapReportDto signedReport = appointmentService.signMedicalReport(id, reportDto,
                    currentDoctor.getId());
            return ResponseEntity.ok(signedReport);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/audit-trail")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR') and @access.isAppointmentDoctor(#id)")
    public ResponseEntity<?> getAppointmentAuditTrail(@PathVariable Long id) {
        try {
            List<com.vikrant.careSync.dto.AppointmentStatusLogDto> auditTrail = appointmentService
                    .getAppointmentAuditTrail(id);
            return ResponseEntity.ok(auditTrail);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
            Patient currentUser = currentUserService.requirePatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByPatient(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/my-appointments/upcoming")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyUpcomingAppointments() {
        try {
            Patient currentUser = currentUserService.requirePatient();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByPatient(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/my-appointments/status/{status}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyAppointmentsByStatus(@PathVariable String status) {
        try {
            Patient currentUser = currentUserService.requirePatient();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    appointmentStatus);
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/my-appointments/completed")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyCompletedAppointments() {
        try {
            Patient currentUser = currentUserService.requirePatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.COMPLETED);
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/my-appointments/cancelled")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyCancelledAppointments() {
        try {
            Patient currentUser = currentUserService.requirePatient();
            List<Appointment> appointments = appointmentService.getAppointmentsByStatusForPatient(currentUser.getId(),
                    Appointment.Status.CANCELLED);
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/patient/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> updateMyAppointment(@PathVariable Long id,
            @Valid @RequestBody CreateAppointmentRequest request) {
        try {
            Patient currentUser = currentUserService.requirePatient();

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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @DeleteMapping("/patient/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> cancelMyAppointment(@PathVariable Long id) {
        try {
            Patient currentUser = currentUserService.requirePatient();

            // Use the updated service method
            appointmentService.cancelAppointment(id, currentUser.getUser());
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Appointment cancelled successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/patient/{id}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> rescheduleMyAppointment(@PathVariable Long id, @RequestParam String newDateTime) {
        try {
            Patient currentUser = currentUserService.requirePatient();

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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // DOCTOR ENDPOINTS - Only accessible by doctors

    @GetMapping("/doctor/dashboard-metrics")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getDoctorDashboardMetrics() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            DoctorDashboardMetricsResponse metrics = appointmentService.getDoctorDashboardMetrics(currentUser.getId());
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
            Doctor currentUser = currentUserService.requireDoctor();
            List<AppointmentResponse> responses = appointmentService.getDoctorAppointmentsPaginated(
                    currentUser.getId(), page, size, status, range, search);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/count")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatientsCount(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "UPCOMING") String range,
            @RequestParam(required = false) String search) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            long count = appointmentService.countDoctorAppointments(currentUser.getId(), status, range, search);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/upcoming")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyUpcomingPatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getUpcomingAppointmentsByDoctor(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/today")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyTodayPatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getTodayAppointments(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/status/{status}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyPatientsByStatus(@PathVariable String status) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            List<Appointment> appointments = appointmentService.getAppointmentsByStatus(currentUser.getId(),
                    appointmentStatus);
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/completed")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyCompletedPatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getCompletedAppointments(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/my-patients/cancelled")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyCancelledPatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getCancelledAppointments(currentUser.getId());
            List<AppointmentResponse> responses = mapToResponses(appointments);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/unique-patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getUniquePatients() {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(currentUser.getId());

            // Get unique patients from appointments and map to UserDto
            List<UserDto> uniquePatients = appointments.stream()
                    .map(Appointment::getPatient)
                    .distinct()
                    .map(UserDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(uniquePatients);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Doctor can change appointment status (CONFIRM, COMPLETE, CANCEL)
    @PutMapping("/doctor/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> updateAppointmentStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();

            // Convert string status to enum
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());

            // Use the updated service method
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id, appointmentStatus,
                    currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
                userEntity = currentUserService.requireDoctor().getUser();
            } else if (isPatient) {
                userEntity = currentUserService.requirePatient().getUser();
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Doctor can confirm appointment
    @PutMapping("/doctor/{id}/confirm")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> confirmAppointment(@PathVariable Long id) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CONFIRMED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Doctor can complete appointment
    @PutMapping("/doctor/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.COMPLETED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Doctor can cancel appointment
    @PutMapping("/doctor/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> cancelAppointmentByDoctor(@PathVariable Long id) {
        try {
            Doctor currentUser = currentUserService.requireDoctor();
            Appointment updatedAppointment = appointmentService.updateAppointmentStatus(id,
                    Appointment.Status.CANCELLED, currentUser.getUser());
            return ResponseEntity.ok(new AppointmentResponse(updatedAppointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
                    Doctor doctor = currentUserService.requireDoctor();
                    if (!appointment.getDoctor().getId().equals(doctor.getId())) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Access denied: Appointment belongs to another doctor");
                        return ResponseEntity.status(403).body(error);
                    }
                } else if (isPatient) {
                    Patient patient = currentUserService.requirePatient();
                    if (!appointment.getPatient().getId().equals(patient.getId())) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Access denied: Appointment belongs to another patient");
                        return ResponseEntity.status(403).body(error);
                    }
                }
            }

            return ResponseEntity.ok(new AppointmentResponse(appointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
                if (isDoctor && !details.getDoctorId().equals(currentUserService.requireDoctor().getId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Access denied: Appointment belongs to another doctor"));
                }
                if (isPatient && !details.getPatientId().equals(currentUserService.requirePatient().getId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Access denied: Appointment belongs to another patient"));
                }
            }

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", ClientErrorMessages.of(e)));
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // PUBLIC ENDPOINTS - Accessible by authenticated users

    @GetMapping("/available-slots")
    public ResponseEntity<?> getAvailableSlots(@RequestParam Long doctorId, @RequestParam String date) {
        try {
            if (doctorId == null || doctorId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid doctor ID"));
            }

            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(date);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Expected YYYY-MM-DD"));
            }

            LocalDate today = LocalDate.now();
            if (parsedDate.isBefore(today.minusDays(1))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot query slots for past dates"));
            }
            if (parsedDate.isAfter(today.plusYears(1))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Date is beyond allowed scheduling window"));
            }

            SlotAvailabilityResponse response = appointmentService.getAvailableSlots(doctorId, date);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // EMERGENCY BOOKING - Immediate appointment booking
    @PostMapping("/patient/book-emergency")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookEmergencyAppointment(@RequestParam Long doctorId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime appointmentDateTime) {
        try {
            Patient currentUser = currentUserService.requirePatient();

            // Book emergency appointment at client local time (or server now if null)
            Appointment emergencyAppointment = appointmentService.bookEmergencyAppointment(
                    doctorId,
                    currentUser.getId(),
                    reason != null && !reason.isBlank() ? reason : "Emergency booking",
                    appointmentDateTime);

            return ResponseEntity.ok(new AppointmentResponse(emergencyAppointment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}/completion-preview")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN') and @access.canAccessAppointment(#id)")
    public ResponseEntity<?> getCompletionPreview(@PathVariable Long id) {
        try {
            CompletionPreviewDto preview = appointmentService.getCompletionPreview(id);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/{id}/esign-and-complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> eSignAndCompleteAppointment(
            @PathVariable Long id,
            @Valid @RequestBody ESignCompletionRequest request) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            ESignCompletionResponse response = appointmentService.eSignAndCompleteAppointment(id, request,
                    currentDoctor.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }
}