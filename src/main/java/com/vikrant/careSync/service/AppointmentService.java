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

        // Check if doctor has any conflicting appointment at current time
        if (isAppointmentTimeConflict(doctorId, emergencyTime, null)) {
            throw new RuntimeException("Doctor is currently busy. Please try again in a few minutes.");
        }

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
            // Patients can only change status to BOOKED or CANCELLED (reschedule is handled
            // separately)
            if (newStatus != Appointment.Status.BOOKED &&
                    newStatus != Appointment.Status.CANCELLED &&
                    newStatus != Appointment.Status.CANCELLED_BY_PATIENT) {
                throw new RuntimeException(
                        "Patients can only change status to BOOKED or CANCELLED. Use reschedule endpoint for rescheduling.");
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

        // Change status with validation and audit trail
        appointment.changeStatus(newStatus, currentUser.getUsername());

        Appointment saved = appointmentRepository.save(appointment);
        if (currentUser.getRole() == User.Role.DOCTOR) {
            if (newStatus == Appointment.Status.CONFIRMED) {
                afterCommitTaskDispatcher.submitAfterCommit("appointment confirmation " + saved.getId(),
                        () -> notificationService.sendAppointmentConfirmation(saved.getId()));
            } else if (newStatus == Appointment.Status.SCHEDULED) {
                afterCommitTaskDispatcher.submitAfterCommit("appointment scheduled " + saved.getId(),
                        () -> notificationService.sendAppointmentScheduled(saved.getId()));
            } else if (newStatus == Appointment.Status.IN_PROGRESS) {
                afterCommitTaskDispatcher.submitAfterCommit("appointment started " + saved.getId(),
                        () -> notificationService.sendAppointmentStarted(saved.getId()));
            } else if (newStatus == Appointment.Status.COMPLETED) {
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
        return appointmentRepository.findAll().stream()
                .filter(appointment -> appointment.getAppointmentDateTime().isAfter(startDate) &&
                        appointment.getAppointmentDateTime().isBefore(endDate))
                .toList();
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

        // Filter out booked/confirmed slots and past slots if today
        List<String> availableSlots = allSlots.stream()
                .filter(slot -> {
                    // Check if slot is already taken
                    if (isSlotUnavailable(existingAppointments, slot)) {
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
}
