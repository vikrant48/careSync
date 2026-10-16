package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.dto.AppointmentReminderRequest;
import com.vikrant.careSync.dto.NotificationDto;
import com.vikrant.careSync.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/appointment-reminder/{appointmentId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessAppointment(#appointmentId)")
    public ResponseEntity<String> sendAppointmentReminder(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentReminder(appointmentId);
            return ResponseEntity.ok("Appointment reminder sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send appointment reminder: " + ClientErrorMessages.of(e));
        }
    }

    @GetMapping("/doctor/{doctorId}/feed")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<List<NotificationDto>> getDoctorFeed(@PathVariable Long doctorId) {
        List<NotificationDto> notifications = notificationService.getDoctorFeed(doctorId).stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/doctor/{doctorId}/unread-count")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<Long> getDoctorUnreadCount(@PathVariable Long doctorId) {
        return ResponseEntity.ok(notificationService.getDoctorUnreadCount(doctorId));
    }

    @GetMapping("/patient/{patientId}/feed")
    @PreAuthorize("hasRole('PATIENT') and @access.isSelf(#patientId)")
    public ResponseEntity<List<NotificationDto>> getPatientFeed(@PathVariable Long patientId) {
        List<NotificationDto> notifications = notificationService.getPatientFeed(patientId).stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/patient/{patientId}/unread-count")
    @PreAuthorize("hasRole('PATIENT') and @access.isSelf(#patientId)")
    public ResponseEntity<Long> getPatientUnreadCount(@PathVariable Long patientId) {
        return ResponseEntity.ok(notificationService.getPatientUnreadCount(patientId));
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.ownsNotification(#notificationId)")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/appointment-reminder")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessAppointment(#request.appointmentId)")
    public ResponseEntity<String> sendAppointmentReminderWithDetails(
            @Valid @RequestBody AppointmentReminderRequest request) {
        try {
            notificationService.sendAppointmentReminderWithDetails(
                    request.getAppointmentId(),
                    request.getReminderType(),
                    request.getHoursBeforeAppointment());
            return ResponseEntity.ok("Appointment reminder scheduled successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to schedule appointment reminder: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/appointment-confirmation/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isAppointmentDoctor(#appointmentId)")
    public ResponseEntity<String> sendAppointmentConfirmation(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentConfirmation(appointmentId);
            return ResponseEntity.ok("Appointment confirmation sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send appointment confirmation: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/appointment-cancellation/{appointmentId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessAppointment(#appointmentId)")
    public ResponseEntity<String> sendAppointmentCancellation(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentCancellation(appointmentId);
            return ResponseEntity.ok("Appointment cancellation notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send cancellation notification: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/appointment-reschedule/{appointmentId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessAppointment(#appointmentId)")
    public ResponseEntity<String> sendAppointmentReschedule(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentReschedule(appointmentId);
            return ResponseEntity.ok("Appointment reschedule notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send reschedule notification: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/daily-schedule/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<String> sendDailySchedule(@PathVariable Long doctorId) {
        try {
            notificationService.sendDailyAppointmentReminders();
            return ResponseEntity.ok("Daily schedule sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send daily schedule: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/weekly-schedule/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<String> sendWeeklySchedule(@PathVariable Long doctorId) {
        try {
            notificationService.sendWeeklySchedule(doctorId);
            return ResponseEntity.ok("Weekly schedule sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send weekly schedule: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/feedback-reminder/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isAppointmentDoctor(#appointmentId)")
    public ResponseEntity<String> sendFeedbackReminder(@PathVariable Long appointmentId) {
        try {
            notificationService.sendFeedbackReminder(appointmentId);
            return ResponseEntity.ok("Feedback reminder sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send feedback reminder: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/system-notification")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendSystemNotification(
            @RequestParam String message,
            @RequestParam(required = false) String targetAudience) {
        try {
            // Using a default recipient type and ID for now
            notificationService.sendSystemNotification(message, targetAudience != null ? targetAudience : "ALL", 0L);
            return ResponseEntity.ok("System notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send system notification: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/bulk-notification")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendBulkNotification(
            @RequestParam String message,
            @RequestParam String userType) {
        try {
            // This would need to be implemented to get user IDs based on userType
            // For now, using empty list
            notificationService.sendBulkNotification(message, List.of());
            return ResponseEntity.ok("Bulk notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send bulk notification: " + ClientErrorMessages.of(e));
        }
    }

    @PostMapping("/custom-notification")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendCustomNotification(
            @RequestParam String message,
            @RequestParam Long userId,
            @RequestParam String userType) {
        try {
            // This would need to be implemented in NotificationService
            // For now, using system notification as fallback
            notificationService.sendSystemNotification(message, userType, userId);
            return ResponseEntity.ok("Custom notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send custom notification: " + ClientErrorMessages.of(e));
        }
    }

}