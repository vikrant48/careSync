package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Only patients can book appointments - status automatically set to BOOKED
    public Appointment bookAppointment(Long doctorId, Long patientId, LocalDateTime appointmentDateTime, String reason) {
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

        return appointmentRepository.save(appointment);
    }

    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
    }

    public Optional<Appointment> getAppointmentByIdOptional(Long appointmentId) {
        return appointmentRepository.findById(appointmentId);
    }

    // Get appointments for doctors with enhanced patient information including illness details
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

    public List<Appointment> getUpcomingAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findUpcomingAppointmentsByDoctorWithDetails(doctorId, LocalDateTime.now());
    }

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

    // Update appointment details (only for patients updating their own appointments)
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
                if (isAppointmentTimeConflict(existingAppointment.getDoctor().getId(), updatedAppointment.getAppointmentDateTime())) {
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
    public Appointment updateAppointmentStatus(Long appointmentId, Appointment.Status newStatus, User currentUser) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // Validate status change permissions
        if (currentUser.getRole() == User.Role.DOCTOR) {
            // Doctor can only update appointments assigned to them
            if (!appointment.getDoctor().getId().equals(currentUser.getId())) {
                throw new RuntimeException("You can only update appointments assigned to you");
            }
        } else if (currentUser.getRole() == User.Role.PATIENT) {
            // Patient can only update their own appointments
            if (!appointment.getPatient().getId().equals(currentUser.getId())) {
                throw new RuntimeException("You can only update your own appointments");
            }
            // Patients can only cancel appointments
            if (newStatus != Appointment.Status.CANCELLED) {
                throw new RuntimeException("Patients can only cancel appointments");
            }
        }

        // Change status with validation and audit trail
        appointment.changeStatus(newStatus, currentUser.getUsername());
        
        return appointmentRepository.save(appointment);
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
        return appointmentRepository.save(appointment);
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

        appointment.changeStatus(Appointment.Status.CANCELLED, currentUser.getUsername());
        appointmentRepository.save(appointment);
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
        return getAppointmentsByStatus(doctorId, Appointment.Status.CANCELLED);
    }

    public List<Appointment> getTodayAppointments(Long doctorId) {
        LocalDateTime today = LocalDateTime.now();
        return getDoctorAppointmentsByDate(doctorId, today);
    }

    public List<Appointment> getAppointmentsByDateRange(Long doctorId, LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, startDate, endDate);
    }

    public List<Appointment> getAppointmentsByDateRangeForPatient(Long patientId, LocalDateTime startDate, LocalDateTime endDate) {
        return appointmentRepository.findByPatientId(patientId).stream()
                .filter(appointment -> appointment.getAppointmentDateTime().isAfter(startDate) && 
                                     appointment.getAppointmentDateTime().isBefore(endDate))
                .toList();
    }

    public List<String> getAvailableSlots(Long doctorId, String date) {
        // This is a simplified implementation
        // In a real application, you would check the doctor's schedule and available time slots
        List<String> availableSlots = List.of(
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
            "14:00", "14:30", "15:00", "15:30", "16:00", "16:30"
        );
        
        // Filter out already booked slots
        LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
        List<Appointment> existingAppointments = getDoctorAppointmentsByDate(doctorId, dateTime);
        
        return availableSlots.stream()
                .filter(slot -> !isSlotBooked(existingAppointments, slot))
                .toList();
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

    private boolean isSlotBooked(List<Appointment> appointments, String timeSlot) {
        return appointments.stream()
                .anyMatch(appointment -> {
                    String appointmentTime = appointment.getAppointmentDateTime().toLocalTime().toString().substring(0, 5);
                    return appointmentTime.equals(timeSlot) && appointment.getStatus() == Appointment.Status.BOOKED;
                });
    }
} 