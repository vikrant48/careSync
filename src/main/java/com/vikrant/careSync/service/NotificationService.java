package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.entity.Notification;
import com.vikrant.careSync.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import com.vikrant.careSync.dto.NotificationDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    // Use repository directly to avoid circular dependency with AppointmentService
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final DoctorService doctorService;

    private final SimpMessagingTemplate messagingTemplate;

    public void sendDoctorNewAppointmentNotification(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generatePatientNewAppointmentMessage,
                this::generateDoctorNewAppointmentMessage);
    }

    public void sendAppointmentScheduled(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentScheduledMessage, this::generateDoctorAppointmentScheduledMessage);
    }

    public void sendAppointmentStarted(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentStartedMessage, this::generateDoctorAppointmentStartedMessage);
    }

    public void sendAppointmentCompleted(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentCompletedMessage, this::generateDoctorAppointmentCompletedMessage);
    }

    public void sendAppointmentReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() == Appointment.Status.BOOKED) {
            String message = generateAppointmentReminderMessage(appointment);
            // In a real application, this would send actual notifications
            // For now, we'll just log the message
            log.info("Appointment reminder prepared for appointment ID {}", appointmentId);
        }
    }

    public void sendAppointmentReminderWithDetails(Long appointmentId, String reminderType,
            Integer hoursBeforeAppointment) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() == Appointment.Status.BOOKED
                || appointment.getStatus() == Appointment.Status.CONFIRMED) {
            String message = generateAppointmentReminderMessageWithDetails(appointment, reminderType,
                    hoursBeforeAppointment);

            // In a real application, this would schedule the notification based on
            // hoursBeforeAppointment
            // and send via the specified reminderType (EMAIL, SMS, PUSH)
            log.info("Scheduling {} reminder {} hours before appointment ID {} ({} characters)", reminderType,
                    hoursBeforeAppointment, appointmentId, message.length());

            // Log the scheduled time for the reminder
            LocalDateTime reminderTime = appointment.getAppointmentDateTime().minusHours(hoursBeforeAppointment);
            log.info("Reminder scheduled for: {}",
                    reminderTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")));
        } else {
            throw new RuntimeException("Cannot send reminder for appointment with status: " + appointment.getStatus());
        }
    }

    public void sendAppointmentConfirmation(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentConfirmationMessage, this::generateDoctorAppointmentConfirmationMessage);
    }

    public void sendAppointmentCancellation(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentCancellationMessage, this::generateDoctorAppointmentCancellationMessage);
    }

    public void sendAppointmentReschedule(Long appointmentId) {
        notifyPatientAndDoctor(appointmentId, this::generateAppointmentRescheduleMessage, this::generateDoctorAppointmentRescheduleMessage);
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

        List<Appointment> weeklyAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateTimeBetween(doctorId, weekStart, weekEnd);

        String message = generateWeeklyScheduleMessage(doctor, weeklyAppointments);
        log.info("Weekly schedule prepared ({} characters)", message.length());
    }

    public void sendFeedbackReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() == Appointment.Status.COMPLETED && appointment.getFeedback() == null) {
            String message = generateFeedbackReminderMessage(appointment);
            log.info("Feedback reminder prepared ({} characters)", message.length());
        }
    }

    private String generateAppointmentReminderMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        return String.format(
                "Reminder: You have an appointment with Dr. %s %s on %s. Please arrive 10 minutes early.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generateAppointmentReminderMessageWithDetails(Appointment appointment, String reminderType,
            Integer hoursBeforeAppointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        String timeText = hoursBeforeAppointment == 1 ? "1 hour" : hoursBeforeAppointment + " hours";
        String channelText = reminderType.equals("EMAIL") ? "email"
                : reminderType.equals("SMS") ? "text message" : "push notification";

        return String.format(
                "[%s Reminder - %s before] You have an appointment with Dr. %s %s on %s. Please arrive 10 minutes early. Patient: %s %s",
                reminderType,
                timeText,
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime,
                appointment.getPatient().getFirstName(),
                appointment.getPatient().getLastName());
    }

    private String generateAppointmentConfirmationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        return String.format(
                "Your appointment with Dr. %s %s has been confirmed for %s. We look forward to seeing you!",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generateAppointmentCancellationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        return String.format(
                "Your appointment with Dr. %s %s scheduled for %s has been cancelled. Please contact us to reschedule.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generateAppointmentRescheduleMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);

        return String.format(
                "Your appointment with Dr. %s %s has been rescheduled to %s. Please confirm your availability.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
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
                appointment.getDoctor().getLastName());
    }

    public void sendSystemNotification(String message, String recipientType, Long recipientId) {
        // In a real application, this would send notifications to specific users
        log.info("System notification to {} (ID: {})", recipientType, recipientId);
    }

    public void sendBulkNotification(String message, List<Long> recipientIds) {
        // In a real application, this would send bulk notifications
        log.info("Bulk notification to {} recipients", recipientIds.size());
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
                appointment.getReason() != null ? appointment.getReason() : "—");
    }

    private String generateAppointmentScheduledMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
                "Your appointment with Dr. %s %s is scheduled for %s.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generateAppointmentStartedMessage(Appointment appointment) {
        return String.format(
                "Your appointment with Dr. %s %s is now in progress.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName());
    }

    private String generateAppointmentCompletedMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
                "Your appointment with Dr. %s %s on %s has been completed.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generatePatientNewAppointmentMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        return String.format(
                "You booked an appointment with Dr. %s %s for %s.",
                appointment.getDoctor().getFirstName(),
                appointment.getDoctor().getLastName(),
                formattedDateTime);
    }

    private String generateDoctorAppointmentConfirmationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment with %s %s confirmed for %s.",
                patient.getFirstName(),
                patient.getLastName(),
                formattedDateTime);
    }

    private String generateDoctorAppointmentCancellationMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment with %s %s on %s was cancelled.",
                patient.getFirstName(),
                patient.getLastName(),
                formattedDateTime);
    }

    private String generateDoctorAppointmentRescheduleMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment with %s %s rescheduled to %s.",
                patient.getFirstName(),
                patient.getLastName(),
                formattedDateTime);
    }

    private String generateDoctorAppointmentScheduledMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment scheduled with %s %s for %s.",
                patient.getFirstName(),
                patient.getLastName(),
                formattedDateTime);
    }

    private String generateDoctorAppointmentStartedMessage(Appointment appointment) {
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment started with %s %s.",
                patient.getFirstName(),
                patient.getLastName());
    }

    private String generateDoctorAppointmentCompletedMessage(Appointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
        String formattedDateTime = appointment.getAppointmentDateTime().format(formatter);
        Patient patient = appointment.getPatient();
        return String.format(
                "Appointment completed with %s %s on %s.",
                patient.getFirstName(),
                patient.getLastName(),
                formattedDateTime);
    }

    /** Loads the appointment once and notifies its patient and its doctor, each with their own message. */
    private void notifyPatientAndDoctor(Long appointmentId,
            java.util.function.Function<Appointment, String> patientMessage,
            java.util.function.Function<Appointment, String> doctorMessage) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        sendToPatient(appointment, patientMessage.apply(appointment));
        sendToDoctor(appointment, doctorMessage.apply(appointment));
    }

    private void sendToDoctor(Appointment appointment, String message) {
        Doctor doctor = appointment.getDoctor();
        deliver(AppConstants.Roles.DOCTOR, doctor.getId(), doctor.getUsername(),
                resolveChannel(doctor.getEmail(), doctor.getContactInfo()), message, "/doctor");
    }

    private void sendToPatient(Appointment appointment, String message) {
        Patient patient = appointment.getPatient();
        deliver(AppConstants.Roles.PATIENT, patient.getId(), patient.getUsername(),
                resolveChannel(patient.getEmail(), patient.getContactInfo()), message, "/patient");
    }

    /**
     * Saves the notification to the recipient's feed and pushes it over WebSocket to /user/queue/notifications
     * (the STOMP principal name is the username). The log line never contains the message: it carries
     * patient names and visit details.
     */
    private void deliver(String recipientType, Long recipientId, String username, String channel, String message,
            String link) {
        log.info("[{}][{}] appointment notification for recipient ID {}", recipientType, channel, recipientId);
        Notification savedNotif = notificationRepository.save(Notification.builder()
                .recipientType(recipientType)
                .recipientId(recipientId)
                .title("Appointment Update")
                .message(message)
                .type("appointment")
                .read(false)
                .timestamp(LocalDateTime.now())
                .link(link)
                .build());
        try {
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", new NotificationDto(savedNotif));
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to {} {}", recipientType, recipientId, e);
        }
    }

    public List<Notification> getDoctorFeed(Long doctorId) {
        return notificationRepository.findTop100ByRecipientTypeAndRecipientIdOrderByTimestampDesc(AppConstants.Roles.DOCTOR,
                doctorId);
    }

    public long getDoctorUnreadCount(Long doctorId) {
        return notificationRepository.countByRecipientTypeAndRecipientIdAndReadIsFalse(AppConstants.Roles.DOCTOR,
                doctorId);
    }

    public List<Notification> getPatientFeed(Long patientId) {
        return notificationRepository.findTop100ByRecipientTypeAndRecipientIdOrderByTimestampDesc(AppConstants.Roles.PATIENT,
                patientId);
    }

    public long getPatientUnreadCount(Long patientId) {
        return notificationRepository.countByRecipientTypeAndRecipientIdAndReadIsFalse(AppConstants.Roles.PATIENT,
                patientId);
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