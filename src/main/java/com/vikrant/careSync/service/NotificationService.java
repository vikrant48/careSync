package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppointmentService appointmentService;
    private final DoctorService doctorService;
    private final PatientService patientService;

    public void sendAppointmentReminder(Long appointmentId) {
        Appointment appointment = appointmentService.getAppointmentById(appointmentId);

        if (appointment.getStatus() == Appointment.Status.BOOKED) {
            String message = generateAppointmentReminderMessage(appointment);
            // In a real application, this would send actual notifications
            // For now, we'll just log the message
            System.out.println("Sending appointment reminder: " + message);
        }
    }

    public void sendAppointmentConfirmation(Long appointmentId) {
        Appointment appointment = appointmentService.getAppointmentById(appointmentId);

        String message = generateAppointmentConfirmationMessage(appointment);
        System.out.println("Sending appointment confirmation: " + message);
    }

    public void sendAppointmentCancellation(Long appointmentId) {
        Appointment appointment = appointmentService.getAppointmentById(appointmentId);

        String message = generateAppointmentCancellationMessage(appointment);
        System.out.println("Sending appointment cancellation: " + message);
    }

    public void sendAppointmentReschedule(Long appointmentId) {
        Appointment appointment = appointmentService.getAppointmentById(appointmentId);

        String message = generateAppointmentRescheduleMessage(appointment);
        System.out.println("Sending appointment reschedule: " + message);
    }

    public void sendDailyAppointmentReminders() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<Doctor> doctors = doctorService.getAllDoctors();

        for (Doctor doctor : doctors) {
            List<Appointment> tomorrowAppointments = appointmentService.getDoctorAppointmentsByDate(doctor.getId(), tomorrow);
            
            for (Appointment appointment : tomorrowAppointments) {
                if (appointment.getStatus() == Appointment.Status.BOOKED) {
                    sendAppointmentReminder(appointment.getId());
                }
            }
        }
    }

    public void sendWeeklySchedule(Long doctorId) {
        Doctor doctor = doctorService.getDoctorById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        LocalDateTime weekStart = LocalDateTime.now().plusDays(1);
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<Appointment> weeklyAppointments = appointmentService.getAppointmentsByDateRange(doctorId, weekStart, weekEnd);
        
        String message = generateWeeklyScheduleMessage(doctor, weeklyAppointments);
        System.out.println("Sending weekly schedule: " + message);
    }

    public void sendFeedbackReminder(Long appointmentId) {
        Appointment appointment = appointmentService.getAppointmentById(appointmentId);

        if (appointment.getStatus() == Appointment.Status.COMPLETED && appointment.getFeedback() == null) {
            String message = generateFeedbackReminderMessage(appointment);
            System.out.println("Sending feedback reminder: " + message);
        }
    }

    private String generateAppointmentReminderMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        
        return String.format(
            "Reminder: You have an appointment with Dr. %s %s on %s. Please arrive 10 minutes early.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateAppointmentConfirmationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        
        return String.format(
            "Your appointment with Dr. %s %s has been confirmed for %s. We look forward to seeing you!",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateAppointmentCancellationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        
        return String.format(
            "Your appointment with Dr. %s %s scheduled for %s has been cancelled. Please contact us to reschedule.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateAppointmentRescheduleMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        
        return String.format(
            "Your appointment with Dr. %s %s has been rescheduled to %s. Please confirm your availability.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateWeeklyScheduleMessage(Doctor doctor, List<Appointment> appointments) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Weekly Schedule for Dr. %s %s:\n\n", 
            doctor.getFirstName(), doctor.getLastName()));

        if (appointments.isEmpty()) {
            message.append("No appointments scheduled for this week.");
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
            for (Appointment appointment : appointments) {
                String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
                message.append(String.format("- %s with %s %s\n", 
                    formattedDateTime,
                    appointment.getPatient().getFirstName(),
                    appointment.getPatient().getLastName()));
            }
        }

        return message.toString();
    }

    private String generateFeedbackReminderMessage(Appointment appointment) {
        return String.format(
            "Thank you for your recent appointment with Dr. %s %s. We would appreciate your feedback to help us improve our services.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName()
        );
    }

    public void sendSystemNotification(String message, String recipientType, Long recipientId) {
        // In a real application, this would send notifications to specific users
        System.out.println(String.format("System notification to %s (ID: %d): %s", 
            recipientType, recipientId, message));
    }

    public void sendBulkNotification(String message, List<Long> recipientIds) {
        // In a real application, this would send bulk notifications
        System.out.println(String.format("Bulk notification to %d recipients: %s", 
            recipientIds.size(), message));
    }
} 