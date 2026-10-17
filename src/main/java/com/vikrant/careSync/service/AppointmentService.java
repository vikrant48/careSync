package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.ChatRepository;
import com.vikrant.careSync.repository.EducationRepository;
import com.vikrant.careSync.repository.ExperienceRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.dto.AppointmentDetailsResponse;
import com.vikrant.careSync.dto.DoctorDashboardMetricsResponse;
import com.vikrant.careSync.dto.BookAppointmentWithPaymentRequest;
import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.dto.SlotAvailabilityResponse;
import com.vikrant.careSync.dto.PreVisitBriefDto;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vikrant.careSync.dto.AppointmentResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.vikrant.careSync.entity.AppointmentStatusLog;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.repository.AppointmentStatusLogRepository;
import com.vikrant.careSync.event.AppointmentBookedEvent;
import com.vikrant.careSync.event.VisitStartedEvent;
import com.vikrant.careSync.event.MedicalReportSignedEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.vikrant.careSync.dto.PreVisitIntakeRequest;
import com.vikrant.careSync.dto.PreVisitIntakeResponse;
import com.vikrant.careSync.service.ai.AiClinicalService;

import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;

import com.vikrant.careSync.dto.SoapReportDto;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.entity.MedicalHistory;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ExperienceRepository experienceRepository;
    private final EducationRepository educationRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationService notificationService;
    private final AfterCommitTaskDispatcher afterCommitTaskDispatcher;
    private final DoctorLeaveService doctorLeaveService;
    private final ChatRepository chatRepository;
    private final CacheManager cacheManager;
    private final PageCache pageCache;
    private final PaymentService paymentService;
    private final SlotLockService slotLockService;
    private final AppointmentStatusLogRepository appointmentStatusLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiClinicalService aiClinicalService;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final AppointmentIntakeRepository appointmentIntakeRepository;
    private final RecordReadinessService recordReadinessService;
    private final com.vikrant.careSync.repository.AppointmentSignatureRepository appointmentSignatureRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final MedicalHistoryService medicalHistoryService;

    /** Pre-visit statuses in which the patient may (re)submit the intake form. */
    private static final Set<Appointment.Status> INTAKE_ALLOWED_STATUSES = EnumSet.of(
            Appointment.Status.BOOKED, Appointment.Status.REQUESTED, Appointment.Status.CONFIRMED,
            Appointment.Status.SCHEDULED, Appointment.Status.INTAKE, Appointment.Status.INTAKE_COMPLETED,
            Appointment.Status.WAITING_ROOM, Appointment.Status.READY_FOR_VISIT);

    // Only patients can book appointments - status automatically set to BOOKED
    @Caching(evict = {
            @CacheEvict(value = "PATIENT:APPOINTMENTS", key = "'upcoming_appointments_' + #patientId"),
            @CacheEvict(value = "DOCTOR:APPOINTMENTS", key = "'upcoming_appointments_' + #doctorId"),
            // A new booking changes the admin platform numbers (cached per date range)
            @CacheEvict(value = "ANALYTICS:OVERALL", allEntries = true)
    })
    public Appointment bookAppointment(Long doctorId, Long patientId, LocalDateTime appointmentDateTime,
            String reason, Appointment.BookingSource bookingSource) {
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Validate doctor and patient are active
        if (!doctor.canAcceptAppointments()) {
            throw new RuntimeException("Doctor is not available for appointments");
        }

        if (!patient.canBookAppointment()) {
            throw new RuntimeException("Patient account is not active");
        }

        // Check if doctor is on leave
        if (doctorLeaveService.isDoctorOnLeave(doctorId, appointmentDateTime.toLocalDate())) {
            throw new RuntimeException("Doctor is on leave on this date");
        }

        // Check Redis slot lock
        if (slotLockService.isSlotLocked(doctorId, appointmentDateTime)) {
            throw new RuntimeException(
                    "This time slot is temporarily locked by another user. Please select a different slot or try again in a few minutes.");
        }

        // Check if the appointment time is available
        if (isAppointmentTimeConflict(doctorId, appointmentDateTime, null)) {
            throw new RuntimeException("Appointment time is not available");
        }

        // Check if appointment time is in the future
        if (appointmentDateTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot book appointment in the past");
        }

        // Create appointment with BOOKED status
        Appointment appointment = Appointment.builder()
                .doctor(doctor)
                .patient(patient)
                .appointmentDateTime(appointmentDateTime)
                .status(Appointment.Status.BOOKED)
                .reason(reason)
                .bookingSource(bookingSource != null ? bookingSource : Appointment.BookingSource.MANUAL)
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        // Record initial status audit log
        AppointmentStatusLog statusLog = AppointmentStatusLog.of(saved.getId(), null, Appointment.Status.BOOKED, patient.getUsername(),
                "Initial Booking: " + (reason != null ? reason : "Standard"));
        appointmentStatusLogRepository.save(statusLog);

        // Release slot lock upon successful booking
        slotLockService.releaseSlotLock(doctorId, appointmentDateTime);

        // Publish domain event
        boolean isEmergency = reason != null && reason.toUpperCase().startsWith("EMERGENCY");
        eventPublisher.publishEvent(new AppointmentBookedEvent(this, saved.getId(), doctorId, patientId, isEmergency));

        afterCommitTaskDispatcher.submitAfterCommit("new appointment notification " + saved.getId(),
                () -> notificationService.sendDoctorNewAppointmentNotification(saved.getId()));
        return saved;
    }

    // A new booking changes the admin platform numbers (cached per date range)
    @CacheEvict(value = "ANALYTICS:OVERALL", allEntries = true)
    @Transactional
    public AppointmentResponse bookAppointmentWithPayment(Long patientId, BookAppointmentWithPaymentRequest request) {
        log.info("Booking appointment with atomic payment for patient ID: {}, doctor ID: {}", patientId,
                request.getDoctorId());

        Appointment appointment = bookAppointment(
                request.getDoctorId(),
                patientId,
                request.getAppointmentDateTime(),
                request.getReason(),
                request.getBookingSource());

        PaymentRequestDto paymentRequest = new PaymentRequestDto();
        paymentRequest.setAmount(request.getAmount());
        paymentRequest.setPaymentMethod(request.getPaymentMethod());
        paymentRequest.setPaymentType(com.vikrant.careSync.entity.Payment.PaymentType.APPOINTMENT);
        paymentRequest.setPatientId(patientId);
        paymentRequest.setAppointmentId(appointment.getId());
        paymentRequest.setUpiId(request.getUpiId());
        paymentRequest.setCardDetails(request.getCardDetails());

        PaymentResponseDto paymentResponse = paymentService.initiatePayment(paymentRequest);

        log.info("Successfully booked appointment ID {} and processed payment record atomically", appointment.getId());
        AppointmentResponse response = new AppointmentResponse(appointment);
        if (paymentResponse != null && paymentResponse.getTransactionId() != null) {
            response.setTransactionId(paymentResponse.getTransactionId());
        }
        return response;
    }

    // Emergency appointment booking - books at current time (or client local time)
    // A new booking changes the admin platform numbers (cached per date range). On both overloads: the inner
    // call below bypasses the cache proxy
    @CacheEvict(value = "ANALYTICS:OVERALL", allEntries = true)
    public Appointment bookEmergencyAppointment(Long doctorId, Long patientId, String reason) {
        return bookEmergencyAppointment(doctorId, patientId, reason, null);
    }

    // A new booking changes the admin platform numbers (cached per date range)
    @CacheEvict(value = "ANALYTICS:OVERALL", allEntries = true)
    public Appointment bookEmergencyAppointment(Long doctorId, Long patientId, String reason,
            LocalDateTime clientTime) {
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Validate doctor and patient are active
        if (!doctor.canAcceptAppointments()) {
            throw new RuntimeException("Doctor is not available for emergency appointments");
        }

        if (!patient.canBookAppointment()) {
            throw new RuntimeException("Patient account is not active");
        }

        // Check if doctor is on leave
        if (doctorLeaveService.isDoctorOnLeave(doctorId, LocalDate.now())) {
            throw new RuntimeException("Doctor is currently on leave");
        }

        // Set appointment time to client time if provided, otherwise fallback to server
        // time
        LocalDateTime emergencyTime = (clientTime != null) ? clientTime : LocalDateTime.now();

        // Create emergency appointment with BOOKED status
        Appointment appointment = Appointment.builder()
                .doctor(doctor)
                .patient(patient)
                .appointmentDateTime(emergencyTime)
                .status(Appointment.Status.BOOKED)
                .reason("EMERGENCY: " + (reason != null && !reason.isBlank() ? reason : "Emergency booking"))
                .bookingSource(Appointment.BookingSource.EMERGENCY)
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        afterCommitTaskDispatcher.submitAfterCommit("new emergency appointment notification " + saved.getId(),
                () -> notificationService.sendDoctorNewAppointmentNotification(saved.getId()));
        return saved;
    }

    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
    }

    @Transactional(readOnly = true)
    public AppointmentDetailsResponse getAppointmentDetails(Long id) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        Long doctorId = appointment.getDoctor().getId();
        return new AppointmentDetailsResponse(
                appointment,
                experienceRepository.findByDoctorId(doctorId),
                educationRepository.findByDoctorId(doctorId),
                feedbackRepository.findByDoctorId(doctorId));
    }

    // Get appointments for doctors with enhanced patient information including
    // illness details
    public List<Appointment> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdWithPatientAndDoctorDetails(doctorId);
    }

    // Get appointments for patients with doctor information
    public List<Appointment> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdWithPatientAndDoctorDetails(patientId);
    }

    @Cacheable(value = "DOCTOR:APPOINTMENTS", key = "'upcoming_appointments_' + #doctorId")
    public List<Appointment> getUpcomingAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findUpcomingAppointmentsByDoctorWithDetails(doctorId, LocalDateTime.now());
    }

    @Cacheable(value = "PATIENT:APPOINTMENTS", key = "'upcoming_appointments_' + #patientId")
    public List<Appointment> getUpcomingAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findUpcomingAppointmentsByPatientWithDetails(patientId, LocalDateTime.now());
    }

    public List<Appointment> getUpcomingPatientAppointments(Long patientId) {
        return getUpcomingAppointmentsByPatient(patientId);
    }

    // Update appointment details (only for patients updating their own
    // appointments)
    public Appointment updateAppointment(Long id, Appointment updatedAppointment, User currentUser) {
        Appointment existingAppointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // Validate ownership
        if (!existingAppointment.getPatient().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You can only update your own appointments");
        }

        // Only allow updates if status is BOOKED
        if (existingAppointment.getStatus() != Appointment.Status.BOOKED) {
            throw new RuntimeException("Cannot update appointment with status: " + existingAppointment.getStatus());
        }

        // Update fields if provided
        if (updatedAppointment.getAppointmentDateTime() != null) {
            // Check for conflicts if time is being changed
            if (!updatedAppointment.getAppointmentDateTime().equals(existingAppointment.getAppointmentDateTime())) {
                doctorRepository.findByIdForUpdate(existingAppointment.getDoctor().getId())
                        .orElseThrow(() -> new RuntimeException("Doctor not found"));
                if (isAppointmentTimeConflict(existingAppointment.getDoctor().getId(),
                        updatedAppointment.getAppointmentDateTime(), existingAppointment.getId())) {
                    throw new RuntimeException("New appointment time is not available");
                }
            }
            existingAppointment.setAppointmentDateTime(updatedAppointment.getAppointmentDateTime());
        }

        if (updatedAppointment.getReason() != null) {
            existingAppointment.setReason(updatedAppointment.getReason());
        }

        return appointmentRepository.save(existingAppointment);
    }

    // Update appointment status (for doctors and patients)
    @CacheEvict(value = { "PATIENT:APPOINTMENTS", "DOCTOR:APPOINTMENTS", "ANALYTICS:OVERALL" }, allEntries = true)
    public Appointment updateAppointmentStatus(Long appointmentId, Appointment.Status newStatus, User currentUser) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // Validate status change permissions
        if (currentUser.getRole() == User.Role.DOCTOR) {
            // Doctor can only update appointments assigned to them
            if (!appointment.getDoctor().getId().equals(currentUser.getId())) {
                throw new RuntimeException("You can only update appointments assigned to you");
            }
            // Doctors can change status to SCHEDULED, IN_PROGRESS, COMPLETED, CONFIRMED, or
            // CANCELLED
            if (newStatus != Appointment.Status.SCHEDULED &&
                    newStatus != Appointment.Status.IN_PROGRESS &&
                    newStatus != Appointment.Status.COMPLETED &&
                    newStatus != Appointment.Status.CONFIRMED &&
                    newStatus != Appointment.Status.CANCELLED &&
                    newStatus != Appointment.Status.CANCELLED_BY_DOCTOR) {
                throw new RuntimeException(
                        "Doctors can only change status to SCHEDULED, IN_PROGRESS, COMPLETED, CONFIRMED, or CANCELLED");
            }

            // Map generic CANCELLED to CANCELLED_BY_DOCTOR for doctors
            if (newStatus == Appointment.Status.CANCELLED) {
                newStatus = Appointment.Status.CANCELLED_BY_DOCTOR;
            }
        } else if (currentUser.getRole() == User.Role.PATIENT) {
            // Patient can only update their own appointments
            if (!appointment.getPatient().getId().equals(currentUser.getId())) {
                throw new RuntimeException("You can only update your own appointments");
            }
            // Patients can only change status to BOOKED, CONFIRMED, or CANCELLED
            if (newStatus != Appointment.Status.BOOKED &&
                    newStatus != Appointment.Status.CONFIRMED &&
                    newStatus != Appointment.Status.CANCELLED &&
                    newStatus != Appointment.Status.CANCELLED_BY_PATIENT) {
                throw new RuntimeException(
                        "Patients can only change status to BOOKED, CONFIRMED, or CANCELLED. Use reschedule endpoint for rescheduling.");
            }

            // Map generic CANCELLED to CANCELLED_BY_PATIENT for patients
            if (newStatus == Appointment.Status.CANCELLED) {
                newStatus = Appointment.Status.CANCELLED_BY_PATIENT;
            }

            // Patients cannot change status to SCHEDULED, IN_PROGRESS, or COMPLETED
            if (newStatus == Appointment.Status.SCHEDULED ||
                    newStatus == Appointment.Status.IN_PROGRESS ||
                    newStatus == Appointment.Status.COMPLETED) {
                throw new RuntimeException(
                        "Only doctors can change appointment status to SCHEDULED, IN_PROGRESS, or COMPLETED");
            }
        }

        // A visit is complete only once its medical record is signed (e-sign flow)
        if (newStatus == Appointment.Status.COMPLETED && medicalHistoryRepository.findAllByAppointmentId(appointmentId)
                .stream().noneMatch(mh -> Boolean.TRUE.equals(mh.getIsSigned()))) {
            throw new RuntimeException("Sign the medical record (e-sign) before completing this visit.");
        }

        Appointment.Status previousStatus = appointment.getStatus();

        // Change status with validation and audit trail
        appointment.changeStatus(newStatus, currentUser.getUsername());

        Appointment saved = appointmentRepository.save(appointment);

        // Record status audit log
        AppointmentStatusLog statusLog = AppointmentStatusLog.of(saved.getId(), previousStatus, newStatus, currentUser.getUsername(),
                "Status update requested by " + currentUser.getRole());
        appointmentStatusLogRepository.save(statusLog);

        if (currentUser.getRole() == User.Role.DOCTOR) {
            if (newStatus == Appointment.Status.CONFIRMED) {
                afterCommitTaskDispatcher.submitAfterCommit("appointment confirmation " + saved.getId(),
                        () -> notificationService.sendAppointmentConfirmation(saved.getId()));
            } else if (newStatus == Appointment.Status.SCHEDULED) {
                afterCommitTaskDispatcher.submitAfterCommit("appointment scheduled " + saved.getId(),
                        () -> notificationService.sendAppointmentScheduled(saved.getId()));
            } else if (newStatus == Appointment.Status.IN_PROGRESS) {
                eventPublisher.publishEvent(new VisitStartedEvent(this, saved.getId(), saved.getDoctor().getId(),
                        saved.getPatient().getId()));
                afterCommitTaskDispatcher.submitAfterCommit("appointment started " + saved.getId(),
                        () -> notificationService.sendAppointmentStarted(saved.getId()));
            } else if (newStatus == Appointment.Status.COMPLETED) {
                eventPublisher.publishEvent(new MedicalReportSignedEvent(this, saved.getId(), null,
                        saved.getDoctor().getId(), saved.getPatient().getId()));
                afterCommitTaskDispatcher.submitAfterCommit("appointment completed " + saved.getId(),
                        () -> notificationService.sendAppointmentCompleted(saved.getId()));
                // Delete chat history
                try {
                    chatRepository.deleteByAppointmentId(saved.getId());
                } catch (Exception e) {
                    // Log error but don't fail the transaction just for chat history
                    System.err.println("Failed to delete chat history: " + e.getMessage());
                }

                // Optionally prompt feedback after completion
                afterCommitTaskDispatcher.submitAfterCommit("feedback reminder " + saved.getId(),
                        () -> notificationService.sendFeedbackReminder(saved.getId()));
            }
        }
        return saved;
    }

    // Legacy method for backward compatibility
    public Appointment updateAppointmentStatus(Long id, String status) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        try {
            Appointment.Status appointmentStatus = Appointment.Status.valueOf(status.toUpperCase());
            appointment.changeStatus(appointmentStatus, "SYSTEM");
            return appointmentRepository.save(appointment);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + status);
        }
    }

    public Appointment rescheduleAppointment(Long appointmentId, LocalDateTime newDateTime, User currentUser) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // Validate ownership
        if (!appointment.getPatient().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You can only reschedule your own appointments");
        }

        // Only allow rescheduling if status is BOOKED
        if (appointment.getStatus() != Appointment.Status.BOOKED) {
            throw new RuntimeException("Cannot reschedule appointment with status: " + appointment.getStatus());
        }

        // Check if the new time is available
        Doctor doctor = doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        if (!doctor.canAcceptAppointments()) {
            throw new RuntimeException("Doctor is inactive and this appointment cannot be rescheduled");
        }
        if (!appointment.getPatient().canBookAppointment()) {
            throw new RuntimeException("Patient account is inactive");
        }
        if (isAppointmentTimeConflict(appointment.getDoctor().getId(), newDateTime, appointment.getId())) {
            throw new RuntimeException("New appointment time is not available");
        }

        // Check if new time is in the future
        if (newDateTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot reschedule appointment to the past");
        }

        appointment.setAppointmentDateTime(newDateTime);
        Appointment saved = appointmentRepository.save(appointment);
        afterCommitTaskDispatcher.submitAfterCommit("appointment reschedule " + saved.getId(),
                () -> notificationService.sendAppointmentReschedule(saved.getId()));
        return saved;
    }

    public void cancelAppointment(Long appointmentId, User currentUser) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // Validate ownership
        if (!appointment.getPatient().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You can only cancel your own appointments");
        }

        // Only allow cancellation if status is BOOKED or CONFIRMED
        if (appointment.getStatus() != Appointment.Status.BOOKED &&
                appointment.getStatus() != Appointment.Status.CONFIRMED) {
            throw new RuntimeException("Cannot cancel appointment with status: " + appointment.getStatus());
        }

        appointment.changeStatus(Appointment.Status.CANCELLED_BY_PATIENT, currentUser.getUsername());
        appointmentRepository.save(appointment);
        afterCommitTaskDispatcher.submitAfterCommit("appointment cancellation " + appointment.getId(),
                () -> notificationService.sendAppointmentCancellation(appointment.getId()));
    }

    /**
     * Admin removal, only for an appointment that holds nothing that must be kept (a duplicate or test booking).
     * Visits with clinical, payment or communication records are part of the medical record: cancel them instead.
     */
    public void deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        boolean hasRecords = medicalHistoryRepository.findByAppointmentId(id).isPresent()
                || appointmentIntakeRepository.findByAppointmentId(id).isPresent()
                || appointmentSignatureRepository.findByAppointmentId(id).isPresent()
                || feedbackRepository.findByAppointmentId(id).isPresent()
                || !chatRepository.findByAppointmentIdOrderByTimestampAsc(id).isEmpty()
                || paymentService.hasPaymentsForAppointment(id);
        if (hasRecords) {
            throw new IllegalStateException("This appointment has clinical, payment or chat records and cannot be "
                    + "deleted. Cancel it instead.");
        }
        appointmentRepository.delete(appointment);
    }

    public List<Appointment> getAppointmentsByStatus(Long doctorId, Appointment.Status status) {
        return appointmentRepository.findByDoctorIdAndStatus(doctorId, status);
    }

    public List<Appointment> getAppointmentsByStatusForPatient(Long patientId, Appointment.Status status) {
        return appointmentRepository.findByPatientIdAndStatus(patientId, status);
    }

    public List<Appointment> getCompletedAppointments(Long doctorId) {
        return getAppointmentsByStatus(doctorId, Appointment.Status.COMPLETED);
    }

    public List<Appointment> getCancelledAppointments(Long doctorId) {
        List<Appointment> cancelled = appointmentRepository.findByDoctorIdAndStatus(doctorId,
                Appointment.Status.CANCELLED);
        cancelled.addAll(
                appointmentRepository.findByDoctorIdAndStatus(doctorId, Appointment.Status.CANCELLED_BY_PATIENT));
        cancelled.addAll(
                appointmentRepository.findByDoctorIdAndStatus(doctorId, Appointment.Status.CANCELLED_BY_DOCTOR));
        return cancelled;
    }

    public List<Appointment> getTodayAppointments(Long doctorId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        return appointmentRepository.findTodayAppointmentsByDoctorWithDetails(doctorId, dayStart, dayStart.plusDays(1));
    }

    @Transactional(readOnly = true)
    public DoctorDashboardMetricsResponse getDoctorDashboardMetrics(Long doctorId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = monthStart.minusMonths(1);
        LocalDateTime yesterdayEnd = yesterday.atTime(LocalTime.MAX);

        // Counts come from the database; only appointments whose status changed since yesterday are loaded
        long todayAppointments = appointmentRepository.countByDoctorIdBetween(doctorId, today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        long yesterdayAppointments = appointmentRepository.countByDoctorIdBetween(doctorId, yesterday.atStartOfDay(),
                today.atStartOfDay());
        long pendingReports = appointmentRepository.countByDoctorIdAndStatusIn(doctorId, PENDING_REPORT_STATUSES);

        // Yesterday's pending count = today's, corrected for the appointments that changed since then
        List<Appointment> changed = appointmentRepository.findStatusChangedSince(doctorId, yesterdayEnd,
                yesterday.atStartOfDay());
        Map<Long, List<AppointmentStatusLog>> logsByAppointment = changed.isEmpty()
                ? Map.of()
                : appointmentStatusLogRepository
                        .findByAppointmentIdInAndCreatedAtGreaterThanEqual(
                                changed.stream().map(Appointment::getId).toList(), yesterday.atStartOfDay())
                        .stream()
                        .collect(Collectors.groupingBy(AppointmentStatusLog::getAppointmentId));
        long pendingNowAmongChanged = changed.stream()
                .filter(appointment -> isPendingReport(appointment.getStatus()))
                .count();
        long pendingYesterdayAmongChanged = changed.stream()
                .filter(appointment -> isPendingReport(
                        statusAt(appointment, logsByAppointment.get(appointment.getId()), yesterdayEnd)))
                .count();
        long pendingReportsYesterday = pendingReports - pendingNowAmongChanged + pendingYesterdayAmongChanged;

        Map<Long, LocalDate> firstVisitByPatient = new HashMap<>();
        for (Object[] row : appointmentRepository.findFirstVisitPerPatient(doctorId)) {
            if (row[0] != null && row[1] != null) {
                firstVisitByPatient.put((Long) row[0], ((LocalDateTime) row[1]).toLocalDate());
            }
        }
        long newPatientsThisMonth = firstVisitByPatient.values().stream()
                .filter(visitDate -> !visitDate.isBefore(monthStart))
                .count();
        long patientsBeforeThisMonth = firstVisitByPatient.size() - newPatientsThisMonth;

        long reviewCount = feedbackRepository.countByDoctorIdValue(doctorId);
        LocalDateTime farFuture = today.plusYears(100).atStartOfDay();
        Double satisfactionRating = rounded(feedbackRepository.averageRatingBetween(doctorId,
                LocalDateTime.of(1900, 1, 1, 0, 0), farFuture));
        Double thisMonthRating = rounded(feedbackRepository.averageRatingBetween(doctorId,
                monthStart.atStartOfDay(), farFuture));
        Double previousMonthRating = rounded(feedbackRepository.averageRatingBetween(doctorId,
                previousMonthStart.atStartOfDay(), monthStart.atStartOfDay()));

        DoctorDashboardMetricsResponse metrics = new DoctorDashboardMetricsResponse();
        metrics.setTodayAppointments(todayAppointments);
        metrics.setAppointmentChangeFromYesterday(todayAppointments - yesterdayAppointments);
        metrics.setTotalPatients(firstVisitByPatient.size());
        metrics.setNewPatientsThisMonth(newPatientsThisMonth);
        if (patientsBeforeThisMonth > 0) {
            metrics.setPatientChangePercent(roundToOneDecimal(newPatientsThisMonth * 100.0 / patientsBeforeThisMonth));
        }
        metrics.setPendingReports(pendingReports);
        metrics.setPendingReportChangeFromYesterday(pendingReports - pendingReportsYesterday);
        metrics.setSatisfactionRating(satisfactionRating);
        metrics.setReviewCount(reviewCount);
        if (thisMonthRating != null && previousMonthRating != null) {
            metrics.setSatisfactionChangeThisMonth(roundToOneDecimal(thisMonthRating - previousMonthRating));
        }
        return metrics;
    }

    public List<Appointment> getAppointmentsByDateRange(Long doctorId, LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, startDate, endDate);
    }

    public SlotAvailabilityResponse getAvailableSlots(Long doctorId, String date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        if (!doctor.canAcceptAppointments()) {
            throw new RuntimeException("Doctor is inactive and has no bookable slots");
        }

        LocalDate requestedLocalDate = LocalDate.parse(date);

        // Check if doctor is on leave
        if (doctorLeaveService.isDoctorOnLeave(doctorId, requestedLocalDate)) {
            com.vikrant.careSync.entity.DoctorLeave leave = doctorLeaveService.getActiveLeave(doctorId,
                    requestedLocalDate);
            String message = "Doctor is on leave";
            LocalDate endDate = null;

            if (leave != null) {
                endDate = leave.getEndDate();
                message = "Doctor is on leave until " + endDate.toString();
            }

            return SlotAvailabilityResponse.builder()
                    .availableSlots(new java.util.ArrayList<>())
                    .isOnLeave(true)
                    .leaveMessage(message)
                    .leaveEndDate(endDate)
                    .build();
        }

        // Generate all possible 30-minute slots for doctor working hours
        List<String> allSlots = generateDoctorWorkingSlots();

        // Query only booked slot times via sargable projection query (index-only scan)
        LocalDateTime startOfDay = requestedLocalDate.atStartOfDay();
        LocalDateTime nextDayStart = startOfDay.plusDays(1);
        List<LocalDateTime> bookedSlotTimes = appointmentRepository.findBookedSlotTimesByDoctorAndDate(doctorId,
                startOfDay, nextDayStart);
        Set<String> bookedTimeStrings = bookedSlotTimes.stream()
                .map(dt -> String.format("%02d:%02d", dt.getHour(), dt.getMinute()))
                .collect(Collectors.toSet());

        // Filter out booked/confirmed slots, past slots if today, and locked slots (one Redis call)
        List<String> availableSlots = openSlotsOn(doctorId, requestedLocalDate, allSlots, bookedTimeStrings);

        return SlotAvailabilityResponse.builder()
                .availableSlots(availableSlots)
                .isOnLeave(false)
                .build();
    }

    /** A day and time that can be booked. */
    public record OpenSlot(LocalDate date, String time) {
    }

    private List<String> openSlotsOn(Long doctorId, LocalDate day, List<String> allSlots, Set<String> bookedTimes) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<String> candidates = allSlots.stream()
                .filter(slot -> !bookedTimes.contains(slot))
                .filter(slot -> !day.equals(today) || LocalTime.parse(slot).isAfter(now))
                .toList();
        Set<LocalDateTime> locked = slotLockService.findLockedSlots(doctorId,
                candidates.stream().map(slot -> day.atTime(LocalTime.parse(slot))).toList());
        return candidates.stream()
                .filter(slot -> !locked.contains(day.atTime(LocalTime.parse(slot))))
                .toList();
    }

    /**
     * The first bookable slot within the next {@code days} days, for the AI booking assistant. One query for the
     * doctor, one for leaves and one for booked times cover the whole range (was several queries and up to 16
     * Redis calls per day).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<OpenSlot> findFirstAvailableSlot(Long doctorId, int days) {
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        if (doctor == null || !doctor.canAcceptAppointments()) {
            return java.util.Optional.empty();
        }
        LocalDate first = LocalDate.now();
        List<com.vikrant.careSync.entity.DoctorLeave> leaves = doctorLeaveService.getUpcomingLeaves(doctorId);
        Map<LocalDate, Set<String>> bookedByDay = appointmentRepository
                .findBookedSlotTimesByDoctorAndDate(doctorId, first.atStartOfDay(), first.plusDays(days).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate,
                        Collectors.mapping(dt -> String.format("%02d:%02d", dt.getHour(), dt.getMinute()),
                                Collectors.toSet())));
        List<String> allSlots = generateDoctorWorkingSlots();
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = first.plusDays(offset);
            boolean onLeave = leaves.stream().anyMatch(leave -> leave.getStartDate() != null
                    && leave.getEndDate() != null && !day.isBefore(leave.getStartDate())
                    && !day.isAfter(leave.getEndDate()));
            if (onLeave) {
                continue;
            }
            List<String> open = openSlotsOn(doctorId, day, allSlots, bookedByDay.getOrDefault(day, Set.of()));
            if (!open.isEmpty()) {
                return java.util.Optional.of(new OpenSlot(day, open.get(0)));
            }
        }
        return java.util.Optional.empty();
    }

    public boolean holdSlot(Long doctorId, LocalDateTime slotTime, Long patientId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!doctor.canAcceptAppointments()) {
            throw new RuntimeException("Doctor is inactive and this slot cannot be held");
        }
        if (!patient.canBookAppointment()) {
            throw new RuntimeException("Patient account is inactive");
        }
        if (isAppointmentTimeConflict(doctorId, slotTime, null)) {
            return false;
        }
        return slotLockService.acquireSlotLock(doctorId, slotTime, patientId);
    }

    /** Releases the patient's own hold; returns false if another patient holds the slot. */
    public boolean releaseSlotHold(Long doctorId, LocalDateTime slotTime, Long patientId) {
        return slotLockService.releaseSlotLockIfOwner(doctorId, slotTime, patientId);
    }

    private List<String> generateDoctorWorkingSlots() {
        List<String> slots = new ArrayList<>();

        // Morning slots: 9:00 AM - 1:00 PM (every 30 minutes)
        for (int hour = 9; hour < 13; hour++) {
            slots.add(String.format("%02d:00", hour));
            slots.add(String.format("%02d:30", hour));
        }

        // Afternoon slots: 2:00 PM - 6:00 PM (every 30 minutes)
        for (int hour = 14; hour < 18; hour++) {
            slots.add(String.format("%02d:00", hour));
            slots.add(String.format("%02d:30", hour));
        }

        return slots;
    }

    private boolean isAppointmentTimeConflict(Long doctorId, LocalDateTime appointmentDateTime,
            Long excludedAppointmentId) {
        // Check for conflicts within 1 hour before and after the requested time
        LocalDateTime startTime = appointmentDateTime.minusHours(1);
        LocalDateTime endTime = appointmentDateTime.plusHours(1);

        return appointmentRepository.countConflictingAppointments(doctorId, startTime, endTime,
                excludedAppointmentId) > 0;
    }

    public List<AppointmentResponse> getDoctorAppointmentsPaginated(Long doctorId, int page, int size,
            String statusFilter, String range, String searchTerm) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String safeStatus = (statusFilter != null && !statusFilter.trim().isEmpty()) ? statusFilter.trim() : "ALL";
        String safeRange = (range != null && !range.trim().isEmpty()) ? range.trim() : "UPCOMING";
        String safeSearch = (searchTerm != null) ? searchTerm.trim().toLowerCase() : "";

        String targetKey = "doc_" + doctorId + "_page_" + safePage + "_size_" + safeSize + "_status_" + safeStatus
                + "_range_" + safeRange + "_q_" + safeSearch;
        return pageCache.list("DOCTOR:APPOINTMENTS_PAGINATED", targetKey,
                () -> fetchDoctorAppointmentsFromDb(doctorId, safePage, safeSize, safeStatus, safeRange, safeSearch));
    }

    public long countDoctorAppointments(Long doctorId, String statusFilter, String range, String searchTerm) {
        String safeStatus = (statusFilter != null && !statusFilter.trim().isEmpty()) ? statusFilter.trim() : "ALL";
        String safeRange = (range != null && !range.trim().isEmpty()) ? range.trim() : "UPCOMING";
        String safeSearch = (searchTerm != null) ? searchTerm.trim().toLowerCase() : "";

        String countKey = "doc_" + doctorId + "_count_status_" + safeStatus + "_range_" + safeRange + "_q_"
                + safeSearch;
        return pageCache.count("DOCTOR:APPOINTMENTS_PAGINATED", countKey, () -> {
            if (safeSearch.isEmpty()) {
                return doctorAppointmentPage(doctorId, 0, 1, safeStatus, safeRange).getTotalElements();
            }
            // Patient names are encrypted: a name search is matched after decryption
            List<Appointment> allDocAppts = appointmentRepository.findByDoctorIdWithPatientAndDoctorDetails(doctorId);
            return (long) filterAppointmentsByCriteria(allDocAppts, safeStatus, safeRange, safeSearch).size();
        });
    }

    private List<AppointmentResponse> fetchDoctorAppointmentsFromDb(Long doctorId, int page, int size,
            String statusFilter, String range, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            // Patient names are encrypted, so only a name search still needs the in-memory path below
            return doctorAppointmentPage(doctorId, page, size, statusFilter, range).getContent().stream()
                    .map(AppointmentResponse::new)
                    .toList();
        }
        List<Appointment> allDocAppts = appointmentRepository.findByDoctorIdWithPatientAndDoctorDetails(doctorId);
        List<Appointment> filtered = filterAppointmentsByCriteria(allDocAppts, statusFilter, range, searchTerm);

        LocalDateTime now = LocalDateTime.now();
        List<Appointment> sorted = filtered.stream().sorted((a1, a2) -> {
            boolean a1Future = a1.getAppointmentDateTime() != null && a1.getAppointmentDateTime().isAfter(now);
            boolean a2Future = a2.getAppointmentDateTime() != null && a2.getAppointmentDateTime().isAfter(now);
            if (a1Future && a2Future)
                return a1.getAppointmentDateTime().compareTo(a2.getAppointmentDateTime());
            if (!a1Future && !a2Future)
                return a2.getAppointmentDateTime().compareTo(a1.getAppointmentDateTime());
            return a1Future ? -1 : 1;
        }).toList();

        int fromIndex = page * size;
        if (fromIndex >= sorted.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, sorted.size());

        return sorted.subList(fromIndex, toIndex).stream()
                .map(AppointmentResponse::new)
                .toList();
    }

    /** Same status and range rules as filterAppointmentsByCriteria, evaluated by the database. */
    private org.springframework.data.domain.Page<Appointment> doctorAppointmentPage(Long doctorId, int page, int size,
            String statusFilter, String range) {
        boolean allStatuses = "ALL".equalsIgnoreCase(statusFilter);
        Set<Appointment.Status> statuses;
        if (allStatuses) {
            statuses = EnumSet.of(Appointment.Status.BOOKED); // unused, but IN () needs a value
        } else if ("PENDING".equalsIgnoreCase(statusFilter)) {
            statuses = EnumSet.of(Appointment.Status.BOOKED, Appointment.Status.SCHEDULED);
        } else {
            Appointment.Status single = java.util.Arrays.stream(Appointment.Status.values())
                    .filter(s -> s.name().equalsIgnoreCase(statusFilter))
                    .findFirst()
                    .orElse(null);
            if (single == null) {
                return org.springframework.data.domain.Page.empty();
            }
            statuses = EnumSet.of(single);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(9999, 1, 1, 0, 0);
        if ("TODAY".equalsIgnoreCase(range)) {
            from = now.toLocalDate().atStartOfDay();
            to = from.plusDays(1);
        } else if ("UPCOMING".equalsIgnoreCase(range)) {
            from = now;
        } else if ("PAST".equalsIgnoreCase(range)) {
            to = now;
        }
        return appointmentRepository.findDoctorAppointmentPage(doctorId, allStatuses, statuses, from, to, now,
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    private List<Appointment> filterAppointmentsByCriteria(List<Appointment> items, String statusFilter, String range,
            String searchTerm) {
        LocalDateTime now = LocalDateTime.now();
        return items.stream().filter(a -> {
            // Status filter
            if (!"ALL".equalsIgnoreCase(statusFilter)) {
                if ("PENDING".equalsIgnoreCase(statusFilter)) {
                    if (a.getStatus() != Appointment.Status.BOOKED && a.getStatus() != Appointment.Status.SCHEDULED) {
                        return false;
                    }
                } else {
                    if (a.getStatus() == null || !a.getStatus().name().equalsIgnoreCase(statusFilter)) {
                        return false;
                    }
                }
            }
            // Range filter
            if ("TODAY".equalsIgnoreCase(range)) {
                if (a.getAppointmentDateTime() == null
                        || !a.getAppointmentDateTime().toLocalDate().equals(now.toLocalDate())) {
                    return false;
                }
            } else if ("UPCOMING".equalsIgnoreCase(range)) {
                if (a.getAppointmentDateTime() == null || a.getAppointmentDateTime().isBefore(now)) {
                    return false;
                }
            } else if ("PAST".equalsIgnoreCase(range)) {
                if (a.getAppointmentDateTime() == null || !a.getAppointmentDateTime().isBefore(now)) {
                    return false;
                }
            }
            // Search term filter
            if (searchTerm != null && !searchTerm.isEmpty()) {
                String patientName = (a.getPatient() != null && a.getPatient().getName() != null)
                        ? a.getPatient().getName().toLowerCase()
                        : "";
                String patientIdStr = (a.getPatient() != null) ? String.valueOf(a.getPatient().getId()) : "";
                if (!patientName.contains(searchTerm) && !patientIdStr.contains(searchTerm)) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    public PreVisitIntakeResponse submitPreVisitIntake(Long appointmentId, PreVisitIntakeRequest request,
            Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Unauthorized: Intake can only be submitted by the booked patient");
        }
        if (!INTAKE_ALLOWED_STATUSES.contains(appointment.getStatus())) {
            throw new IllegalStateException("Intake can no longer be changed once the consultation has started");
        }

        Appointment.Status previousStatus = appointment.getStatus();

        // Create or update dedicated AppointmentIntake record
        AppointmentIntake intake = appointmentIntakeRepository.findByAppointmentId(appointmentId)
                .orElse(AppointmentIntake.builder().appointment(appointment).build());

        intake.setChiefComplaint(request.getChiefComplaint());
        intake.setSymptoms(request.getSymptoms());
        // Generate Structured AI Intake Summary & Red Flags
        com.vikrant.careSync.dto.AiIntakeSummaryDto structuredSummary = aiClinicalService
                .generateStructuredIntakeSummary(
                        request.getChiefComplaint(),
                        request.getSymptoms(),
                        request.getSymptomDuration(),
                        request.getSeverity(),
                        request.getCurrentMedications(),
                        request.getAllergies());

        intake.setChiefComplaint(request.getChiefComplaint());
        intake.setSymptoms(request.getSymptoms());
        intake.setSymptomDuration(request.getSymptomDuration());
        intake.setSymptomSeverity(request.getSeverity());
        intake.setCurrentMedications(request.getCurrentMedications());
        intake.setAllergies(request.getAllergies());
        intake.setConsentGiven(request.getConsentGiven());
        intake.setAiSummary(structuredSummary.getSummaryText());
        intake.setIntakeStatus("SUBMITTED");
        intake.setIsConfirmedByPatient(false);
        intake.setHasRedFlags(structuredSummary.isHasEmergencyFlags());
        intake.setRedFlags(structuredSummary.getEmergencyMessage());

        AppointmentIntake savedIntake = appointmentIntakeRepository.save(intake);

        // Update Appointment status & readiness
        appointment.setIntakeCompleted(true);
        appointment.setPatientReady(true);

        if (appointment.canChangeStatus(Appointment.Status.WAITING_ROOM)) {
            appointment.changeStatus(Appointment.Status.WAITING_ROOM, "PATIENT_INTAKE");
        } else if (appointment.canChangeStatus(Appointment.Status.INTAKE_COMPLETED)) {
            appointment.changeStatus(Appointment.Status.INTAKE_COMPLETED, "PATIENT_INTAKE");
        }

        Appointment savedApt = appointmentRepository.save(appointment);

        // Record status log
        appointmentStatusLogRepository.record(savedApt.getId(), previousStatus, savedApt.getStatus(), "PATIENT",
                "Pre-visit digital intake submitted - Patient moved to Waiting Room");

        return PreVisitIntakeResponse.builder()
                .appointmentId(savedApt.getId())
                .chiefComplaint(savedIntake.getChiefComplaint())
                .symptoms(savedIntake.getSymptoms())
                .symptomsSummary(savedIntake.getSymptoms())
                .symptomDuration(savedIntake.getSymptomDuration())
                .duration(savedIntake.getSymptomDuration())
                .severity(savedIntake.getSymptomSeverity())
                .currentMedications(savedIntake.getCurrentMedications())
                .allergies(savedIntake.getAllergies())
                .consentGiven(savedIntake.getConsentGiven())
                .preVisitSummary(savedIntake.getAiSummary())
                .aiTriageSummary(savedIntake.getAiSummary())
                .intakeCompleted(savedApt.getIntakeCompleted())
                .intakeStatus(savedIntake.getIntakeStatus())
                .isConfirmedByPatient(savedIntake.getIsConfirmedByPatient())
                .confirmedAt(savedIntake.getConfirmedAt())
                .confirmedBy(savedIntake.getConfirmedBy())
                .hasRedFlags(savedIntake.getHasRedFlags())
                .redFlags(savedIntake.getRedFlags())
                .editHistory(savedIntake.getEditHistory())
                .updatedAt(savedIntake.getUpdatedAt())
                .build();
    }

    public PreVisitIntakeResponse getPreVisitIntakeSummary(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Optional<AppointmentIntake> intakeOpt = appointmentIntakeRepository.findByAppointmentId(appointmentId);

        if (intakeOpt.isPresent()) {
            AppointmentIntake intake = intakeOpt.get();
            return PreVisitIntakeResponse.builder()
                    .appointmentId(appointment.getId())
                    .chiefComplaint(intake.getChiefComplaint())
                    .symptoms(intake.getSymptoms())
                    .symptomsSummary(intake.getSymptoms())
                    .symptomDuration(intake.getSymptomDuration())
                    .duration(intake.getSymptomDuration())
                    .severity(intake.getSymptomSeverity())
                    .currentMedications(intake.getCurrentMedications())
                    .allergies(intake.getAllergies())
                    .consentGiven(intake.getConsentGiven())
                    .preVisitSummary(intake.getAiSummary())
                    .aiTriageSummary(intake.getAiSummary())
                    .intakeCompleted(appointment.getIntakeCompleted() != null && appointment.getIntakeCompleted())
                    .intakeStatus(intake.getIntakeStatus())
                    .isConfirmedByPatient(intake.getIsConfirmedByPatient())
                    .confirmedAt(intake.getConfirmedAt())
                    .confirmedBy(intake.getConfirmedBy())
                    .hasRedFlags(intake.getHasRedFlags())
                    .redFlags(intake.getRedFlags())
                    .editHistory(intake.getEditHistory())
                    .updatedAt(intake.getUpdatedAt())
                    .build();
        }

        return PreVisitIntakeResponse.builder()
                .appointmentId(appointment.getId())
                .chiefComplaint("None provided")
                .symptoms("None specified")
                .preVisitSummary("No pre-visit intake completed yet.")
                .aiTriageSummary("No pre-visit intake completed yet.")
                .intakeCompleted(appointment.getIntakeCompleted() != null && appointment.getIntakeCompleted())
                .intakeStatus("EMPTY")
                .isConfirmedByPatient(false)
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }

    public PreVisitBriefDto getPreVisitBrief(Long appointmentId, Long doctorId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
        if (appointment.getDoctor() == null || !appointment.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Unauthorized: Only the assigned doctor can view this pre-visit brief");
        }

        AppointmentIntake intake = appointmentIntakeRepository.findByAppointmentId(appointmentId).orElse(null);
        MedicalSummaryResponse history = aiClinicalService.summarizePatientHistory(appointment.getPatient().getId());
        String unfinished = appointmentRepository.findByPatientId(appointment.getPatient().getId()).stream()
                .filter(item -> !item.getId().equals(appointmentId))
                .filter(item -> item.getStatus() != Appointment.Status.COMPLETED
                        && item.getStatus() != Appointment.Status.CANCELLED
                        && item.getStatus() != Appointment.Status.REJECTED
                        && item.getStatus() != Appointment.Status.CANCELLED_BY_PATIENT
                        && item.getStatus() != Appointment.Status.CANCELLED_BY_DOCTOR
                        && item.getStatus() != Appointment.Status.NO_SHOW
                        && item.getStatus() != Appointment.Status.NO_SHOW_PATIENT
                        && item.getStatus() != Appointment.Status.NO_SHOW_DOCTOR
                        && item.getStatus() != Appointment.Status.AUTO_CLOSED)
                .sorted(Comparator.comparing(Appointment::getAppointmentDateTime))
                .limit(5)
                .map(item -> item.getAppointmentDateTime() + " — " + item.getReason() + " (" + item.getStatus() + ")")
                .collect(Collectors.joining("\n"));

        return PreVisitBriefDto.builder()
                .appointmentId(appointmentId)
                .patientId(appointment.getPatient().getId())
                .historySummary(history != null && history.isSuccess() && history.getSummary() != null
                        ? history.getSummary()
                        : "No prior visit summary is available.")
                .currentMedications(intake != null && intake.getCurrentMedications() != null
                        && !intake.getCurrentMedications().isBlank() ? intake.getCurrentMedications() : "Not provided")
                .allergies(intake != null && intake.getAllergies() != null && !intake.getAllergies().isBlank()
                        ? intake.getAllergies()
                        : "Not provided")
                .unfinishedFollowUps(unfinished.isBlank() ? "No unfinished follow-ups found." : unfinished)
                .build();
    }

    public SoapReportDto generateAndSaveSoapDraft(Long appointmentId, String liveTranscript) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElse(new MedicalHistory());
        // Never overwrite (or un-sign) a signed record; checked before the AI call
        medicalHistoryService.assertEditable(mh);

        SoapReportDto draftDto = aiClinicalService.generateSoapDraft(appointment, liveTranscript);

        mh.setAppointmentId(appointmentId);
        mh.setPatient(appointment.getPatient());
        mh.setDoctor(appointment.getDoctor());
        mh.setVisitDate(appointment.getAppointmentDateTime() != null
                ? appointment.getAppointmentDateTime().toLocalDate()
                : LocalDate.now());
        mh.setSubjective(draftDto.getSubjective());
        mh.setObjective(draftDto.getObjective());
        mh.setAssessment(draftDto.getAssessment());
        mh.setPlan(draftDto.getPlan());
        mh.setSymptoms(draftDto.getSymptoms());
        mh.setDiagnosis(draftDto.getDiagnosis());
        mh.setTreatment(draftDto.getTreatment());
        mh.setMedicine(draftDto.getMedicine());
        mh.setDoses(draftDto.getDoses());
        mh.setNotes(draftDto.getNotes());
        mh.setTranscript(liveTranscript);
        mh.setIsDraft(true);
        mh.setIsSigned(false);

        MedicalHistory savedMh = medicalHistoryRepository.save(mh);
        draftDto.setId(savedMh.getId());

        // Update appointment status to REPORT_DRAFTED
        if (appointment.canChangeStatus(Appointment.Status.REPORT_DRAFTED)) {
            Appointment.Status previousStatus = appointment.getStatus();
            appointment.changeStatus(Appointment.Status.REPORT_DRAFTED, "AI_AMBIENT_SCRIBE");
            appointmentRepository.save(appointment);

            appointmentStatusLogRepository.record(appointment.getId(), previousStatus, Appointment.Status.REPORT_DRAFTED, "AI_SCRIBE",
                "Automated Groq AI SOAP draft generated from pre-visit intake and consultation transcript");
        }

        return draftDto;
    }

    public SoapReportDto getSoapDraft(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId).orElse(null);

        if (mh != null) {
            return SoapReportDto.builder()
                    .id(mh.getId())
                    .appointmentId(appointmentId)
                    .patientId(mh.getPatient() != null ? mh.getPatient().getId() : null)
                    .patientName(mh.getPatient() != null
                            ? mh.getPatient().getFirstName() + " " + mh.getPatient().getLastName()
                            : "Patient")
                    .doctorId(mh.getDoctor() != null ? mh.getDoctor().getId() : null)
                    .doctorName(mh.getDoctor() != null
                            ? "Dr. " + mh.getDoctor().getFirstName() + " " + mh.getDoctor().getLastName()
                            : "Doctor")
                    .visitDate(mh.getVisitDate())
                    .subjective(mh.getSubjective())
                    .objective(mh.getObjective())
                    .assessment(mh.getAssessment())
                    .plan(mh.getPlan())
                    .symptoms(mh.getSymptoms())
                    .diagnosis(mh.getDiagnosis())
                    .treatment(mh.getTreatment())
                    .medicine(mh.getMedicine())
                    .doses(mh.getDoses())
                    .notes(mh.getNotes())
                    .transcript(mh.getTranscript())
                    .isDraft(mh.getIsDraft())
                    .isSigned(mh.getIsSigned())
                    .signedAt(mh.getSignedAt())
                    .build();
        }

        return aiClinicalService.generateSoapDraft(appointment, "");
    }

    /**
     * The visit report as a patient may see it: only once the doctor has signed it. An unsigned (AI) draft is
     * never shown to the patient, and a patient's request never generates one.
     */
    public SoapReportDto getSignedReportForPatient(Long appointmentId) {
        medicalHistoryRepository.findByAppointmentId(appointmentId)
                .filter(record -> Boolean.TRUE.equals(record.getIsSigned()))
                .orElseThrow(() -> new RuntimeException("The visit report is not available yet"));
        return getSoapDraft(appointmentId);
    }

    public SoapReportDto saveReviewedSoapDraft(Long appointmentId, SoapReportDto reportDto, Long doctorId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
        if (appointment.getDoctor() == null || !appointment.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Unauthorized: Only the assigned doctor can edit this report");
        }

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElse(new MedicalHistory());
        if (Boolean.TRUE.equals(mh.getIsSigned())) {
            throw new RuntimeException("This medical report is signed and can no longer be edited");
        }
        medicalHistoryService.assertEditable(mh);
        mh.setAppointmentId(appointmentId);
        mh.setPatient(appointment.getPatient());
        mh.setDoctor(appointment.getDoctor());
        mh.setVisitDate(appointment.getAppointmentDateTime().toLocalDate());
        mh.setSubjective(reportDto.getSubjective());
        mh.setObjective(reportDto.getObjective());
        mh.setAssessment(reportDto.getAssessment());
        mh.setPlan(reportDto.getPlan());
        mh.setSymptoms(reportDto.getSymptoms());
        mh.setDiagnosis(reportDto.getDiagnosis());
        mh.setTreatment(reportDto.getTreatment());
        mh.setMedicine(reportDto.getMedicine());
        mh.setDoses(reportDto.getDoses());
        mh.setNotes(reportDto.getNotes());
        mh.setTranscript(reportDto.getTranscript());
        mh.setIsDraft(true);
        mh.setIsSigned(false);
        medicalHistoryRepository.save(mh);

        if (appointment.canChangeStatus(Appointment.Status.REPORT_DRAFTED)) {
            appointment.changeStatus(Appointment.Status.REPORT_DRAFTED, "DOCTOR_REVIEWED_AI_DRAFT");
            appointmentRepository.save(appointment);
        }
        return getSoapDraft(appointmentId);
    }

    public SoapReportDto signMedicalReport(Long appointmentId, SoapReportDto reportDto, Long doctorId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Unauthorized: Only the assigned doctor can sign off on this report");
        }

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElse(new MedicalHistory());
        // A signed report is final: signing again would overwrite it
        medicalHistoryService.assertEditable(mh);

        mh.setAppointmentId(appointmentId);
        mh.setPatient(appointment.getPatient());
        mh.setDoctor(appointment.getDoctor());
        mh.setVisitDate(appointment.getAppointmentDateTime().toLocalDate());
        mh.setSubjective(reportDto.getSubjective());
        mh.setObjective(reportDto.getObjective());
        mh.setAssessment(reportDto.getAssessment());
        mh.setPlan(reportDto.getPlan());
        mh.setSymptoms(reportDto.getSymptoms());
        mh.setDiagnosis(reportDto.getDiagnosis());
        mh.setTreatment(reportDto.getTreatment());
        mh.setMedicine(reportDto.getMedicine());
        mh.setDoses(reportDto.getDoses());
        mh.setNotes(reportDto.getNotes());
        if (reportDto.getTranscript() != null) {
            mh.setTranscript(reportDto.getTranscript());
        }
        mh.setIsDraft(false);
        mh.setIsSigned(true);
        mh.setSignedAt(LocalDateTime.now());

        MedicalHistory savedMh = medicalHistoryRepository.save(mh);

        // Transition status to COMPLETED
        Appointment.Status previousStatus = appointment.getStatus();
        if (appointment.canChangeStatus(Appointment.Status.COMPLETED)) {
            appointment.changeStatus(Appointment.Status.COMPLETED, "DOCTOR_E_SIGN");
            appointmentRepository.save(appointment);

            appointmentStatusLogRepository.record(appointment.getId(), previousStatus, Appointment.Status.COMPLETED, "DOCTOR",
                "Doctor reviewed, approved, and electronically signed the medical report");

            // Fire MedicalReportSignedEvent
            eventPublisher.publishEvent(new MedicalReportSignedEvent(
                    this,
                    appointmentId,
                    doctorId,
                    appointment.getPatient().getId(),
                    savedMh.getId()));
        }

        return SoapReportDto.builder()
                .id(savedMh.getId())
                .appointmentId(appointmentId)
                .patientId(savedMh.getPatient().getId())
                .patientName(savedMh.getPatient().getFirstName() + " " + savedMh.getPatient().getLastName())
                .doctorId(savedMh.getDoctor().getId())
                .doctorName("Dr. " + savedMh.getDoctor().getFirstName() + " " + savedMh.getDoctor().getLastName())
                .visitDate(savedMh.getVisitDate())
                .subjective(savedMh.getSubjective())
                .objective(savedMh.getObjective())
                .assessment(savedMh.getAssessment())
                .plan(savedMh.getPlan())
                .symptoms(savedMh.getSymptoms())
                .diagnosis(savedMh.getDiagnosis())
                .treatment(savedMh.getTreatment())
                .medicine(savedMh.getMedicine())
                .doses(savedMh.getDoses())
                .notes(savedMh.getNotes())
                .transcript(savedMh.getTranscript())
                .isDraft(savedMh.getIsDraft())
                .isSigned(savedMh.getIsSigned())
                .signedAt(savedMh.getSignedAt())
                .build();
    }

    public com.vikrant.careSync.dto.AppointmentAnalyticsResponse getAppointmentAnalytics(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            Long doctorId) {

        List<Appointment> appointments;
        boolean ranged = startDate != null && endDate != null;
        if (doctorId != null) {
            appointments = ranged
                    ? appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, startDate, endDate)
                    : appointmentRepository.findByDoctorId(doctorId);
        } else {
            appointments = ranged
                    ? appointmentRepository.findByAppointmentDateTimeBetweenWithDetails(startDate, endDate)
                    : appointmentRepository.findAll();
        }

        long total = appointments.size();
        if (total == 0) {
            return com.vikrant.careSync.dto.AppointmentAnalyticsResponse.builder()
                    .totalAppointments(0)
                    .completedAppointments(0)
                    .noShowAppointments(0)
                    .cancelledAppointments(0)
                    .completionRate(0.0)
                    .noShowRate(0.0)
                    .cancellationRate(0.0)
                    .statusBreakdown(new java.util.HashMap<>())
                    .funnelConversion(new java.util.HashMap<>())
                    .build();
        }

        java.util.Map<String, Long> statusBreakdown = new java.util.HashMap<>();
        for (Appointment.Status status : Appointment.Status.values()) {
            statusBreakdown.put(status.name(), 0L);
        }

        long completed = 0;
        long noShow = 0;
        long cancelled = 0;
        long readyForVisit = 0;
        long intakeDone = 0;
        long inProgress = 0;

        for (Appointment appt : appointments) {
            if (appt.getStatus() != null) {
                String sName = appt.getStatus().name();
                statusBreakdown.put(sName, statusBreakdown.getOrDefault(sName, 0L) + 1);

                if (appt.getStatus() == Appointment.Status.COMPLETED)
                    completed++;
                if (appt.getStatus() == Appointment.Status.NO_SHOW_PATIENT
                        || appt.getStatus() == Appointment.Status.NO_SHOW_DOCTOR)
                    noShow++;
                if (appt.getStatus() == Appointment.Status.CANCELLED
                        || appt.getStatus() == Appointment.Status.CANCELLED_BY_PATIENT
                        || appt.getStatus() == Appointment.Status.CANCELLED_BY_DOCTOR)
                    cancelled++;
                if (appt.getStatus() == Appointment.Status.READY_FOR_VISIT)
                    readyForVisit++;
                if (appt.getStatus() == Appointment.Status.INTAKE_COMPLETED
                        || appt.getIntakeCompleted() != null && appt.getIntakeCompleted())
                    intakeDone++;
                if (appt.getStatus() == Appointment.Status.IN_PROGRESS
                        || appt.getStatus() == Appointment.Status.REPORT_DRAFTED
                        || appt.getStatus() == Appointment.Status.COMPLETED)
                    inProgress++;
            }
        }

        double completionRate = Math.round((completed * 100.0 / total) * 10.0) / 10.0;
        double noShowRate = Math.round((noShow * 100.0 / total) * 10.0) / 10.0;
        double cancellationRate = Math.round((cancelled * 100.0 / total) * 10.0) / 10.0;

        java.util.Map<String, Double> funnelConversion = new java.util.LinkedHashMap<>();
        funnelConversion.put("BOOKED_TO_INTAKE",
                total > 0
                        ? Math.round(((intakeDone + inProgress + completed) * 100.0 / total) * 10.0) / 10.0
                        : 0.0);
        funnelConversion.put("INTAKE_TO_READY_FOR_VISIT",
                (intakeDone + inProgress + completed) > 0
                        ? Math.round(((readyForVisit + inProgress + completed) * 100.0
                                / (intakeDone + inProgress + completed)) * 10.0) / 10.0
                        : 0.0);
        funnelConversion.put("VISIT_TO_CONSULTATION",
                (readyForVisit + inProgress + completed) > 0
                        ? Math.round(
                                ((inProgress + completed) * 100.0 / (readyForVisit + inProgress + completed)) * 10.0)
                                / 10.0
                        : 0.0);
        funnelConversion.put("CONSULTATION_TO_COMPLETED",
                (inProgress + completed) > 0 ? Math.round((completed * 100.0 / (inProgress + completed)) * 10.0) / 10.0
                        : 0.0);

        return com.vikrant.careSync.dto.AppointmentAnalyticsResponse.builder()
                .totalAppointments(total)
                .completedAppointments(completed)
                .noShowAppointments(noShow)
                .cancelledAppointments(cancelled)
                .completionRate(completionRate)
                .noShowRate(noShowRate)
                .cancellationRate(cancellationRate)
                .statusBreakdown(statusBreakdown)
                .funnelConversion(funnelConversion)
                .build();
    }

    public List<com.vikrant.careSync.dto.AppointmentStatusLogDto> getAppointmentAuditTrail(Long appointmentId) {
        List<AppointmentStatusLog> logs = appointmentStatusLogRepository
                .findByAppointmentIdOrderByCreatedAtDesc(appointmentId);
        return logs.stream().map(log -> com.vikrant.careSync.dto.AppointmentStatusLogDto.builder()
                .id(log.getId())
                .appointmentId(log.getAppointmentId())
                .previousStatus(log.getPreviousStatus())
                .newStatus(log.getNewStatus())
                .changedBy(log.getChangedBy())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.vikrant.careSync.dto.CompletionPreviewDto getCompletionPreview(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        PreVisitIntakeResponse intake = getPreVisitIntakeSummary(appointmentId);
        SoapReportDto soap = getSoapDraft(appointmentId);
        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId).orElse(null);

        com.vikrant.careSync.dto.MedicalHistoryDto mhDto = mh != null
                ? new com.vikrant.careSync.dto.MedicalHistoryDto(mh)
                : null;

        com.vikrant.careSync.dto.RecordReadinessDto readiness = recordReadinessService.checkReadiness(appointmentId);

        String patientName = appointment.getPatient() != null
                ? appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName()
                : "Patient";

        return com.vikrant.careSync.dto.CompletionPreviewDto.builder()
                .appointmentId(appointmentId)
                .patientName(patientName)
                .patientAge(appointment.getPatient() != null && appointment.getPatient().getDateOfBirth() != null
                        ? java.time.Period.between(appointment.getPatient().getDateOfBirth(), LocalDate.now())
                                .getYears()
                        : null)
                .patientGender(appointment.getPatient() != null ? appointment.getPatient().getGender() : null)
                .intake(intake)
                .soapReport(soap)
                .medicalHistory(mhDto)
                .isReadyForSign(readiness.isReady())
                .readinessReasons(readiness.getReasons())
                .build();
    }

    public com.vikrant.careSync.dto.ESignCompletionResponse eSignAndCompleteAppointment(Long appointmentId,
            com.vikrant.careSync.dto.ESignCompletionRequest request, Long doctorId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Unauthorized: Only the assigned doctor can sign off and complete this visit");
        }

        // 1. Password re-entry verification
        Doctor doctor = appointment.getDoctor();
        if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), doctor.getUser().getPassword())) {
            throw new IllegalArgumentException("Invalid password. Electronic signature failed.");
        }

        if (!Boolean.TRUE.equals(request.getDeclarationAccepted())) {
            throw new IllegalArgumentException("You must accept the legal e-signature declaration.");
        }

        // 2. Validate readiness
        com.vikrant.careSync.dto.RecordReadinessDto readiness = recordReadinessService.checkReadiness(appointmentId);
        if (!readiness.isReady()) {
            throw new IllegalStateException("Cannot complete visit: " + String.join("; ", readiness.getReasons()));
        }

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalStateException("Medical History record not found for appointment"));
        // Already signed or completed: signing again would replace the legal record and its hash
        medicalHistoryService.assertEditable(mh);

        // 3. Compute SHA-256 Hash of clinical record
        String contentHash = computeClinicalRecordHash(mh);

        // 4. Save AppointmentSignature
        com.vikrant.careSync.entity.AppointmentSignature signature = appointmentSignatureRepository
                .findByAppointmentId(appointmentId)
                .orElse(com.vikrant.careSync.entity.AppointmentSignature.builder().appointmentId(appointmentId)
                        .build());

        signature.setDoctorId(doctorId);
        signature.setSignedAt(LocalDateTime.now());
        signature.setSignedByName(request.getDoctorNameTyped());
        signature.setDoctorRegNumber(
                doctor.getSpecialization() != null ? doctor.getSpecialization() : "REG-DOCTOR-" + doctorId);
        signature.setAuthMethod("PASSWORD_REENTRY");
        signature.setContentHash(contentHash);
        signature.setSignatureImageUrl(request.getSignatureImageUrl());
        appointmentSignatureRepository.save(signature);

        // 5. Lock MedicalHistory
        mh.setIsDraft(false);
        mh.setIsSigned(true);
        mh.setSignedAt(LocalDateTime.now());
        medicalHistoryRepository.save(mh);

        // 6. Transition Appointment status to COMPLETED
        Appointment.Status previousStatus = appointment.getStatus();
        appointment.setVisitEndedAt(LocalDateTime.now());
        appointment.changeStatus(Appointment.Status.COMPLETED, "DOCTOR_E_SIGN");
        appointmentRepository.save(appointment);

        appointmentStatusLogRepository.record(appointment.getId(), previousStatus, Appointment.Status.COMPLETED, doctor.getUsername(),
                "Visit electronically signed and completed by Dr. " + request.getDoctorNameTyped());

        // 7. Publish Event & Notifications
        eventPublisher.publishEvent(new MedicalReportSignedEvent(this, appointment.getId(), mh.getId(),
                doctor.getId(), appointment.getPatient().getId()));

        afterCommitTaskDispatcher.submitAfterCommit("appointment completion " + appointment.getId(),
                () -> notificationService.sendAppointmentCompleted(appointment.getId()));

        String pdfUrl = "/api/appointments/" + appointmentId + "/download-prescription";

        return com.vikrant.careSync.dto.ESignCompletionResponse.builder()
                .appointmentId(appointmentId)
                .status(Appointment.Status.COMPLETED)
                .signedAt(mh.getSignedAt())
                .signedByName(request.getDoctorNameTyped())
                .contentHash(contentHash)
                .pdfUrl(pdfUrl)
                .message("Visit successfully electronically signed and completed.")
                .build();
    }

    private String computeClinicalRecordHash(MedicalHistory mh) {
        try {
            String raw = String.format("APP:%s|DOC:%s|PAT:%s|S:%s|O:%s|A:%s|P:%s|DIAG:%s|TREAT:%s",
                    mh.getAppointmentId(),
                    mh.getDoctor() != null ? mh.getDoctor().getId() : "",
                    mh.getPatient() != null ? mh.getPatient().getId() : "",
                    mh.getSubjective(), mh.getObjective(), mh.getAssessment(), mh.getPlan(),
                    mh.getDiagnosis(), mh.getTreatment());
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "SHA256_HASH_ERROR_" + System.currentTimeMillis();
        }
    }

    private static final Set<Appointment.Status> PENDING_REPORT_STATUSES = EnumSet.of(
            Appointment.Status.MEDICAL_RECORD,
            Appointment.Status.REPORT_DRAFTED,
            Appointment.Status.READY_TO_COMPLETE);

    private boolean isPendingReport(Appointment.Status status) {
        return status != null && PENDING_REPORT_STATUSES.contains(status);
    }

    private Appointment.Status statusAt(Appointment appointment, List<AppointmentStatusLog> logs, LocalDateTime at) {
        if (logs != null) {
            Optional<AppointmentStatusLog> latest = logs.stream()
                    .filter(log -> log.getCreatedAt() != null && !log.getCreatedAt().isAfter(at))
                    .max(Comparator.comparing(AppointmentStatusLog::getCreatedAt));
            if (latest.isPresent()) {
                return latest.get().getNewStatus();
            }
        }
        LocalDateTime changedAt = appointment.getStatusChangedAt() != null
                ? appointment.getStatusChangedAt()
                : appointment.getCreatedAt();
        if (changedAt != null && !changedAt.isAfter(at)) {
            return appointment.getStatus();
        }
        return null;
    }

    private Double rounded(Double average) {
        return average == null ? null : roundToOneDecimal(average);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
