package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.entity.Notification;
import com.vikrant.careSync.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    // Use repository directly to avoid circular dependency with AppointmentService
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final DoctorService doctorService;
    private final PatientService patientService;

    public void sendDoctorNewAppointmentNotification(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        String doctorMessage = generateDoctorNewAppointmentMessage(appointment);
        String patientMessage = generatePatientNewAppointmentMessage(appointment);
        sendToDoctor(appointment, doctorMessage);
        sendToPatient(appointment, patientMessage);
    }

    public void sendAppointmentScheduled(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        String patientMessage = generateAppointmentScheduledMessage(appointment);
        String doctorMessage = generateDoctorAppointmentScheduledMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendAppointmentStarted(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        String patientMessage = generateAppointmentStartedMessage(appointment);
        String doctorMessage = generateDoctorAppointmentStartedMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendAppointmentCompleted(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        String patientMessage = generateAppointmentCompletedMessage(appointment);
        String doctorMessage = generateDoctorAppointmentCompletedMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendAppointmentReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() == Appointment.Status.BOOKED) {
            String message = generateAppointmentReminderMessage(appointment);
            // In a real application, this would send actual notifications
            // For now, we'll just log the message
            System.out.println("Sending appointment reminder: " + message);
        }
    }

    public void sendAppointmentReminderWithDetails(Long appointmentId, String reminderType, Integer hoursBeforeAppointment) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() == Appointment.Status.BOOKED || appointment.getStatus() == Appointment.Status.CONFIRMED) {
            String message = generateAppointmentReminderMessageWithDetails(appointment, reminderType, hoursBeforeAppointment);
            
            // In a real application, this would schedule the notification based on hoursBeforeAppointment
            // and send via the specified reminderType (EMAIL, SMS, PUSH)
            System.out.println(String.format("Scheduling %s reminder %d hours before appointment: %s", 
                reminderType, hoursBeforeAppointment, message));
            
            // Log the scheduled time for the reminder
            LocalDateTime reminderTime = appointment.getAppointmentDateTime().minusHours(hoursBeforeAppointment);
            System.out.println("Reminder scheduled for: " + reminderTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")));
        } else {
            throw new RuntimeException("Cannot send reminder for appointment with status: " + appointment.getStatus());
        }
    }

    public void sendAppointmentConfirmation(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        String patientMessage = generateAppointmentConfirmationMessage(appointment);
        String doctorMessage = generateDoctorAppointmentConfirmationMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendAppointmentCancellation(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        String patientMessage = generateAppointmentCancellationMessage(appointment);
        String doctorMessage = generateDoctorAppointmentCancellationMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendAppointmentReschedule(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        String patientMessage = generateAppointmentRescheduleMessage(appointment);
        String doctorMessage = generateDoctorAppointmentRescheduleMessage(appointment);
        sendToPatient(appointment, patientMessage);
        sendToDoctor(appointment, doctorMessage);
    }

    public void sendDailyAppointmentReminders() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<Doctor> doctors = doctorService.getAllDoctors();

        for (Doctor doctor : doctors) {
            // Fetch all appointments for the doctor and filter by the target date
            List<Appointment> tomorrowAppointments = appointmentRepository.findByDoctorId(doctor.getId()).stream()
                    .filter(a -> a.getAppointmentDateTime().toLocalDate().equals(tomorrow.toLocalDate()))
                    .toList();
            
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

        List<Appointment> weeklyAppointments = appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, weekStart, weekEnd);
        
        String message = generateWeeklyScheduleMessage(doctor, weeklyAppointments);
        System.out.println("Sending weekly schedule: " + message);
    }

    public void sendFeedbackReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

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

    private String generateAppointmentReminderMessageWithDetails(Appointment appointment, String reminderType, Integer hoursBeforeAppointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        
        String timeText = hoursBeforeAppointment == 1 ? "1 hour" : hoursBeforeAppointment + " hours";
        String channelText = reminderType.equals("EMAIL") ? "email" : 
                           reminderType.equals("SMS") ? "text message" : "push notification";
        
        return String.format(
            "[%s Reminder - %s before] You have an appointment with Dr. %s %s on %s. Please arrive 10 minutes early. Patient: %s %s",
            reminderType,
            timeText,
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime,
            appointment.getPatient().getFirstName(),
            appointment.getPatient().getLastName()
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

    private String generateDoctorNewAppointmentMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        Patient patient = appointment.getPatient();
        return String.format(
            "New appointment booked by %s %s for %s. Reason: %s",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime,
            appointment.getReason() != null ? appointment.getReason() : "—"
        );
    }

    private String generateAppointmentScheduledMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
            "Your appointment with Dr. %s %s is scheduled for %s.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateAppointmentStartedMessage(Appointment appointment) {
        return String.format(
            "Your appointment with Dr. %s %s is now in progress.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName()
        );
    }

    private String generateAppointmentCompletedMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
            "Your appointment with Dr. %s %s on %s has been completed.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }
    private String generatePatientNewAppointmentMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
            "You booked an appointment with Dr. %s %s for %s.",
            appointment.getDoctor().getFirstName(),
            appointment.getDoctor().getLastName(),
            formattedDateTime
        );
    }

    private String generateDoctorAppointmentConfirmationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment with %s %s confirmed for %s.",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime
        );
    }

    private String generateDoctorAppointmentCancellationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment with %s %s on %s was cancelled.",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime
        );
    }

    private String generateDoctorAppointmentRescheduleMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment with %s %s rescheduled to %s.",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime
        );
    }

    private String generateDoctorAppointmentScheduledMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment scheduled with %s %s for %s.",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime
        );
    }

    private String generateDoctorAppointmentStartedMessage(Appointment appointment) {
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment started with %s %s.",
            patient.getFirstName(),
            patient.getLastName()
        );
    }

    private String generateDoctorAppointmentCompletedMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
            "Appointment completed with %s %s on %s.",
            patient.getFirstName(),
            patient.getLastName(),
            formattedDateTime
        );
    }

    private void sendToDoctor(Appointment appointment, String message) {
        Doctor doctor = appointment.getDoctor();
        String channel = resolveChannel(doctor.getEmail(), doctor.getContactInfo());
        System.out.println(String.format("[DOCTOR][%s] %s", channel, message));

        // Persist notification for doctor feed
        Notification notif = Notification.builder()
                .recipientType("DOCTOR")
                .recipientId(doctor.getId())
                .title("Appointment Update")
                .message(message)
                .type("appointment")
                .read(false)
                .timestamp(LocalDateTime.now())
                .link("/doctor")
                .build();
        notificationRepository.save(notif);
    }

    private void sendToPatient(Appointment appointment, String message) {
        Patient patient = appointment.getPatient();
        String channel = resolveChannel(patient.getEmail(), patient.getContactInfo());
        System.out.println(String.format("[PATIENT][%s] %s", channel, message));

        // Persist notification for patient feed
        Notification notif = Notification.builder()
                .recipientType("PATIENT")
                .recipientId(patient.getId())
                .title("Appointment Update")
                .message(message)
                .type("appointment")
                .read(false)
                .timestamp(LocalDateTime.now())
                .link("/patient")
                .build();
        notificationRepository.save(notif);
    }

    public List<Notification> getDoctorFeed(Long doctorId) {
        return notificationRepository.findByRecipientTypeAndRecipientIdOrderByTimestampDesc("DOCTOR", doctorId);
    }

    public long getDoctorUnreadCount(Long doctorId) {
        return notificationRepository.countByRecipientTypeAndRecipientIdAndReadIsFalse("DOCTOR", doctorId);
    }

    public List<Notification> getPatientFeed(Long patientId) {
        return notificationRepository.findByRecipientTypeAndRecipientIdOrderByTimestampDesc("PATIENT", patientId);
    }

    public long getPatientUnreadCount(Long patientId) {
        return notificationRepository.countByRecipientTypeAndRecipientIdAndReadIsFalse("PATIENT", patientId);
    }

    public void markRead(Long notificationId) {
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notif.setRead(true);
        notificationRepository.save(notif);
    }

    private String resolveChannel(String email, String contactInfo) {
        if (email != null && !email.isBlank()) {
            return "EMAIL";
        }
        if (contactInfo != null && !contactInfo.isBlank()) {
            return contactInfo.contains("@") ? "EMAIL" : "SMS";
        }
        return "PUSH";
    }
}