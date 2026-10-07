package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.ChatRepository;
import com.vikrant.careSync.dto.BookAppointmentWithPaymentRequest;
import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.dto.SlotAvailabilityResponse;
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
import java.util.List;
import java.util.Optional;

import com.vikrant.careSync.entity.AppointmentStatusLog;
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
    private final NotificationService notificationService;
    private final AfterCommitTaskDispatcher afterCommitTaskDispatcher;
    private final DoctorLeaveService doctorLeaveService;
    private final ChatRepository chatRepository;
    private final CacheManager cacheManager;
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

    // Only patients can book appointments - status automatically set to BOOKED
    @Caching(evict = {
            @CacheEvict(value = "PATIENT:APPOINTMENTS", key = "'upcoming_appointments_' + #patientId"),
            @CacheEvict(value = "DOCTOR:APPOINTMENTS", key = "'upcoming_appointments_' + #doctorId")
    })
    public Appointment bookAppointment(Long doctorId, Long patientId, LocalDateTime appointmentDateTime,
            String reason) {
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
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        // Record initial status audit log
        AppointmentStatusLog statusLog = AppointmentStatusLog.builder()
                .appointmentId(saved.getId())
                .previousStatus(null)
                .newStatus(Appointment.Status.BOOKED)
                .changedBy(patient.getUsername())
                .reason("Initial Booking: " + (reason != null ? reason : "Standard"))
                .build();
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

    @Transactional
    public AppointmentResponse bookAppointmentWithPayment(Long patientId, BookAppointmentWithPaymentRequest request) {
        log.info("Booking appointment with atomic payment for patient ID: {}, doctor ID: {}", patientId,
                request.getDoctorId());

        Appointment appointment = bookAppointment(
                request.getDoctorId(),
                patientId,
                request.getAppointmentDateTime(),
                request.getReason());

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

    // Emergency appointment booking - books at current time
    public Appointment bookEmergencyAppointment(Long doctorId, Long patientId, String reason) {
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

        // Set appointment time to current time (emergency booking)
        LocalDateTime emergencyTime = LocalDateTime.now();

        // Create emergency appointment with BOOKED status
        Appointment appointment = Appointment.builder()
                .doctor(doctor)
                .patient(patient)
                .appointmentDateTime(emergencyTime)
                .status(Appointment.Status.BOOKED)
                .reason("EMERGENCY: " + reason)
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

    public Optional<Appointment> getAppointmentByIdOptional(Long appointmentId) {
        return appointmentRepository.findById(appointmentId);
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

    // Get appointments for doctors with patient illness details
    public List<Appointment> getAppointmentsByDoctorWithPatientDetails(Long doctorId) {
        return appointmentRepository.findByDoctorIdWithPatientAndDoctorDetails(doctorId);
    }

    // Get appointments for patients with doctor details
    public List<Appointment> getAppointmentsByPatientWithDoctorDetails(Long patientId) {
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

    public List<Appointment> getDoctorAppointmentsByDate(Long doctorId, LocalDateTime date) {
        return appointmentRepository.findByDoctorId(doctorId).stream()
                .filter(appointment -> appointment.getAppointmentDateTime().toLocalDate().equals(date.toLocalDate()))
                .toList();
    }

    public List<Appointment> getUpcomingAppointments(Long doctorId) {
        return getUpcomingAppointmentsByDoctor(doctorId);
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

        Appointment.Status previousStatus = appointment.getStatus();

        // Change status with validation and audit trail
        appointment.changeStatus(newStatus, currentUser.getUsername());

        Appointment saved = appointmentRepository.save(appointment);

        // Record status audit log
        AppointmentStatusLog statusLog = AppointmentStatusLog.builder()
                .appointmentId(saved.getId())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(currentUser.getUsername())
                .reason("Status update requested by " + currentUser.getRole())
                .build();
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
        doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
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

    public void deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
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
        LocalDateTime today = LocalDateTime.now();
        return appointmentRepository.findTodayAppointmentsByDoctorWithDetails(doctorId, today);
    }

    public List<Appointment> getAppointmentsByDateRange(Long doctorId, LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, startDate, endDate);
    }

    public List<Appointment> getAppointmentsByDateRangeForPatient(Long patientId, LocalDateTime startDate,
            LocalDateTime endDate) {
        return appointmentRepository.findByPatientId(patientId).stream()
                .filter(appointment -> appointment.getAppointmentDateTime().isAfter(startDate) &&
                        appointment.getAppointmentDateTime().isBefore(endDate))
                .toList();
    }

    // Get all appointments across all doctors within date range (for system-wide
    // analytics)
    public List<Appointment> getAllAppointmentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findByAppointmentDateTimeBetweenWithDetails(startDate, endDate);
    }

    public SlotAvailabilityResponse getAvailableSlots(Long doctorId, String date) {
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

        // Get existing appointments for the date
        LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
        List<Appointment> existingAppointments = getDoctorAppointmentsByDate(doctorId, dateTime);

        // If the date is today, filter out past slots
        LocalDate requestedDate = dateTime.toLocalDate();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Filter out booked/confirmed slots, locked slots, and past slots if today
        List<String> availableSlots = allSlots.stream()
                .filter(slot -> {
                    // Check if slot is already taken
                    if (isSlotUnavailable(existingAppointments, slot)) {
                        return false;
                    }

                    LocalDateTime slotDateTime = LocalDateTime.parse(date + "T" + slot + ":00");
                    if (slotLockService.isSlotLocked(doctorId, slotDateTime)) {
                        return false;
                    }

                    // If today, check if slot is in the future
                    if (requestedDate.equals(today)) {
                        java.time.LocalTime slotTime = java.time.LocalTime.parse(slot);
                        return slotTime.isAfter(now);
                    }

                    return true;
                })
                .toList();

        return SlotAvailabilityResponse.builder()
                .availableSlots(availableSlots)
                .isOnLeave(false)
                .build();
    }

    public boolean holdSlot(Long doctorId, LocalDateTime slotTime, Long patientId) {
        if (isAppointmentTimeConflict(doctorId, slotTime, null)) {
            return false;
        }
        return slotLockService.acquireSlotLock(doctorId, slotTime, patientId);
    }

    public void releaseSlotHold(Long doctorId, LocalDateTime slotTime) {
        slotLockService.releaseSlotLock(doctorId, slotTime);
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

    private boolean isSlotUnavailable(List<Appointment> appointments, String timeSlot) {
        return appointments.stream()
                .anyMatch(appointment -> {
                    String appointmentTime = appointment.getAppointmentDateTime().toLocalTime().toString().substring(0,
                            5);
                    // Consider slots unavailable if they are BOOKED, CONFIRMED, SCHEDULED, or
                    // IN_PROGRESS
                    return appointmentTime.equals(timeSlot) &&
                            (appointment.getStatus() == Appointment.Status.BOOKED ||
                                    appointment.getStatus() == Appointment.Status.CONFIRMED ||
                                    appointment.getStatus() == Appointment.Status.SCHEDULED ||
                                    appointment.getStatus() == Appointment.Status.IN_PROGRESS);
                });
    }

    public List<AppointmentResponse> getDoctorAppointmentsPaginated(Long doctorId, int page, int size,
            String statusFilter, String range, String searchTerm) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String safeStatus = (statusFilter != null && !statusFilter.trim().isEmpty()) ? statusFilter.trim() : "ALL";
        String safeRange = (range != null && !range.trim().isEmpty()) ? range.trim() : "UPCOMING";
        String safeSearch = (searchTerm != null) ? searchTerm.trim().toLowerCase() : "";

        Cache cache = null;
        try {
            cache = cacheManager.getCache("DOCTOR:APPOINTMENTS_PAGINATED");
        } catch (Exception e) {
            log.warn("Redis cache manager error: {}", e.getMessage());
        }

        String targetKey = "doc_" + doctorId + "_page_" + safePage + "_size_" + safeSize + "_status_" + safeStatus
                + "_range_" + safeRange + "_q_" + safeSearch;

        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(targetKey);
                if (wrapper != null && wrapper.get() instanceof List<?> rawList) {
                    @SuppressWarnings("unchecked")
                    List<AppointmentResponse> cachedDtos = (List<AppointmentResponse>) rawList;
                    prefetchDoctorAppointmentWindowPages(doctorId, safePage, safeSize, safeStatus, safeRange,
                            safeSearch, cache);
                    return cachedDtos;
                }
            } catch (Exception e) {
                log.warn("Redis error reading key [{}]: {}. Falling back to DB.", targetKey, e.getMessage());
            }
        }

        List<AppointmentResponse> pageDtos = fetchDoctorAppointmentsFromDb(doctorId, safePage, safeSize,
                safeStatus, safeRange, safeSearch);

        if (cache != null) {
            try {
                cache.put(targetKey, pageDtos);
            } catch (Exception e) {
                log.warn("Redis error writing key [{}]: {}", targetKey, e.getMessage());
            }
        }

        prefetchDoctorAppointmentWindowPages(doctorId, safePage, safeSize, safeStatus, safeRange, safeSearch, cache);
        return pageDtos;
    }

    public long countDoctorAppointments(Long doctorId, String statusFilter, String range, String searchTerm) {
        String safeStatus = (statusFilter != null && !statusFilter.trim().isEmpty()) ? statusFilter.trim() : "ALL";
        String safeRange = (range != null && !range.trim().isEmpty()) ? range.trim() : "UPCOMING";
        String safeSearch = (searchTerm != null) ? searchTerm.trim().toLowerCase() : "";

        Cache cache = null;
        try {
            cache = cacheManager.getCache("DOCTOR:APPOINTMENTS_PAGINATED");
            if (cache != null) {
                String countKey = "doc_" + doctorId + "_count_status_" + safeStatus + "_range_" + safeRange + "_q_"
                        + safeSearch;
                Cache.ValueWrapper wrapper = cache.get(countKey);
                if (wrapper != null && wrapper.get() instanceof Long count) {
                    return count;
                }
            }
        } catch (Exception e) {
            log.warn("Redis error reading doctor appointment count: {}", e.getMessage());
        }

        List<Appointment> allDocAppts = appointmentRepository.findByDoctorIdWithPatientAndDoctorDetails(doctorId);
        long count = filterAppointmentsByCriteria(allDocAppts, safeStatus, safeRange, safeSearch).size();

        if (cache != null) {
            try {
                String countKey = "doc_" + doctorId + "_count_status_" + safeStatus + "_range_" + safeRange + "_q_"
                        + safeSearch;
                cache.put(countKey, count);
            } catch (Exception e) {
                log.warn("Redis error writing doctor appointment count: {}", e.getMessage());
            }
        }

        return count;
    }

    private List<AppointmentResponse> fetchDoctorAppointmentsFromDb(Long doctorId, int page, int size,
            String statusFilter, String range, String searchTerm) {
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

    private void prefetchDoctorAppointmentWindowPages(Long doctorId, int currentPage, int pageSize, String statusFilter,
            String range, String searchTerm, Cache cache) {
        if (cache == null)
            return;
        int[] windowPages = (currentPage == 0) ? new int[] { 1 } : new int[] { currentPage - 1, currentPage + 1 };
        for (int p : windowPages) {
            if (p < 0)
                continue;
            String key = "doc_" + doctorId + "_page_" + p + "_size_" + pageSize + "_status_" + statusFilter + "_range_"
                    + range + "_q_" + searchTerm;
            try {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper == null) {
                    List<AppointmentResponse> pageDtos = fetchDoctorAppointmentsFromDb(doctorId, p, pageSize,
                            statusFilter, range, searchTerm);
                    if (!pageDtos.isEmpty()) {
                        cache.put(key, pageDtos);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to prefetch doctor appointment page [{}]: {}", key, e.getMessage());
            }
        }
    }

    public PreVisitIntakeResponse submitPreVisitIntake(Long appointmentId, PreVisitIntakeRequest request,
            Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Unauthorized: Intake can only be submitted by the booked patient");
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
        appointmentStatusLogRepository.save(AppointmentStatusLog.builder()
                .appointmentId(savedApt.getId())
                .previousStatus(previousStatus)
                .newStatus(savedApt.getStatus())
                .changedBy("PATIENT")
                .reason("Pre-visit digital intake submitted - Patient moved to Waiting Room")
                .build());

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

    public SoapReportDto generateAndSaveSoapDraft(Long appointmentId, String liveTranscript) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        SoapReportDto draftDto = aiClinicalService.generateSoapDraft(appointment, liveTranscript);

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElse(new MedicalHistory());

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

            appointmentStatusLogRepository.save(AppointmentStatusLog.builder()
                    .appointmentId(appointment.getId())
                    .previousStatus(previousStatus)
                    .newStatus(Appointment.Status.REPORT_DRAFTED)
                    .changedBy("AI_SCRIBE")
                    .reason("Automated Groq AI SOAP draft generated from pre-visit intake and consultation transcript")
                    .build());
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

    public SoapReportDto signMedicalReport(Long appointmentId, SoapReportDto reportDto, Long doctorId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Unauthorized: Only the assigned doctor can sign off on this report");
        }

        MedicalHistory mh = medicalHistoryRepository.findByAppointmentId(appointmentId)
                .orElse(new MedicalHistory());

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

            appointmentStatusLogRepository.save(AppointmentStatusLog.builder()
                    .appointmentId(appointment.getId())
                    .previousStatus(previousStatus)
                    .newStatus(Appointment.Status.COMPLETED)
                    .changedBy("DOCTOR")
                    .reason("Doctor reviewed, approved, and electronically signed the medical report")
                    .build());

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
        if (doctorId != null) {
            appointments = appointmentRepository.findByDoctorId(doctorId);
        } else {
            appointments = appointmentRepository.findAll();
        }

        if (startDate != null && endDate != null) {
            appointments = appointments.stream()
                    .filter(a -> a.getAppointmentDateTime() != null
                            && !a.getAppointmentDateTime().isBefore(startDate)
                            && !a.getAppointmentDateTime().isAfter(endDate))
                    .collect(java.util.stream.Collectors.toList());
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

        appointmentStatusLogRepository.save(AppointmentStatusLog.builder()
                .appointmentId(appointment.getId())
                .previousStatus(previousStatus)
                .newStatus(Appointment.Status.COMPLETED)
                .changedBy(doctor.getUsername())
                .reason("Visit electronically signed and completed by Dr. " + request.getDoctorNameTyped())
                .build());

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
}
