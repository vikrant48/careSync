package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;
    private final DoctorLeaveService doctorLeaveService;
    private final com.vikrant.careSync.repository.ChatRepository chatRepository;

    // Only patients can book appointments - status automatically set to BOOKED
    @Caching(evict = {
            @CacheEvict(value = "patientData", key = "'upcoming_appointments_' + #patientId"),
            @CacheEvict(value = "doctorListing", key = "'upcoming_appointments_' + #doctorId")
    })
    public Appointment bookAppointment(Long doctorId, Long patientId, LocalDateTime appointmentDateTime,
            String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
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
        if (isAppointmentTimeConflict(doctorId, appointmentDateTime)) {
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
        // Notify the doctor internally about the new booking
        notificationService.sendDoctorNewAppointmentNotification(saved.getId());
        return saved;
    }

    // Emergency appointment booking - books at current time
    public Appointment bookEmergencyAppointment(Long doctorId, Long patientId, String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
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
        if (isAppointmentTimeConflict(doctorId, emergencyTime)) {
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
        // Notify the doctor internally about the new emergency booking
        notificationService.sendDoctorNewAppointmentNotification(saved.getId());
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

    @Cacheable(value = "doctorListing", key = "'upcoming_appointments_' + #doctorId")
    public List<Appointment> getUpcomingAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findUpcomingAppointmentsByDoctorWithDetails(doctorId, LocalDateTime.now());
    }

    @Cacheable(value = "patientData", key = "'upcoming_appointments_' + #patientId")
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
        Appointment existingAppointment = appointmentRepository.findById(id)
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
                if (isAppointmentTimeConflict(existingAppointment.getDoctor().getId(),
                        updatedAppointment.getAppointmentDateTime())) {
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
    @CacheEvict(value = { "patientData", "doctorListing", "analytics" }, allEntries = true)
    public Appointment updateAppointmentStatus(Long appointmentId, Appointment.Status newStatus, User currentUser) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
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
                notificationService.sendAppointmentConfirmation(saved.getId());
            } else if (newStatus == Appointment.Status.SCHEDULED) {
                notificationService.sendAppointmentScheduled(saved.getId());
            } else if (newStatus == Appointment.Status.IN_PROGRESS) {
                notificationService.sendAppointmentStarted(saved.getId());
            } else if (newStatus == Appointment.Status.COMPLETED) {
                notificationService.sendAppointmentCompleted(saved.getId());
                // Delete chat history
                try {
                    chatRepository.deleteByAppointmentId(saved.getId());
                } catch (Exception e) {
                    // Log error but don't fail the transaction just for chat history
                    System.err.println("Failed to delete chat history: " + e.getMessage());
                }

                // Optionally prompt feedback after completion
                notificationService.sendFeedbackReminder(saved.getId());
            }
        }
        return saved;
    }

    // Legacy method for backward compatibility
    public Appointment updateAppointmentStatus(Long id, String status) {
        Appointment appointment = appointmentRepository.findById(id)
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
        Appointment appointment = appointmentRepository.findById(appointmentId)
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
        if (isAppointmentTimeConflict(appointment.getDoctor().getId(), newDateTime)) {
            throw new RuntimeException("New appointment time is not available");
        }

        // Check if new time is in the future
        if (newDateTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot reschedule appointment to the past");
        }

        appointment.setAppointmentDateTime(newDateTime);
        Appointment saved = appointmentRepository.save(appointment);
        // Notify the doctor/patient of reschedule
        notificationService.sendAppointmentReschedule(saved.getId());
        return saved;
    }

    public void cancelAppointment(Long appointmentId, User currentUser) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
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
        // Notify the doctor/patient of cancellation
        notificationService.sendAppointmentCancellation(appointment.getId());
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

    public com.vikrant.careSync.dto.SlotAvailabilityResponse getAvailableSlots(Long doctorId, String date) {
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

            return com.vikrant.careSync.dto.SlotAvailabilityResponse.builder()
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
        java.time.LocalDate requestedDate = dateTime.toLocalDate();
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();

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

        return com.vikrant.careSync.dto.SlotAvailabilityResponse.builder()
                .availableSlots(availableSlots)
                .isOnLeave(false)
                .build();
    }

    private List<String> generateDoctorWorkingSlots() {
        List<String> slots = new java.util.ArrayList<>();

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

    private boolean isAppointmentTimeConflict(Long doctorId, LocalDateTime appointmentDateTime) {
        // Check for conflicts within 1 hour before and after the requested time
        LocalDateTime startTime = appointmentDateTime.minusHours(1);
        LocalDateTime endTime = appointmentDateTime.plusHours(1);

        return appointmentRepository.findByDoctorId(doctorId).stream()
                .anyMatch(existingAppointment -> {
                    LocalDateTime existingTime = existingAppointment.getAppointmentDateTime();
                    return existingAppointment.getStatus() == Appointment.Status.BOOKED &&
                            existingTime.isAfter(startTime) && existingTime.isBefore(endTime);
                });
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
}