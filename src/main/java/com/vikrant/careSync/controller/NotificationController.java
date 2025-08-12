package com.vikrant.careSync.controller;

import com.vikrant.careSync.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/appointment-reminder/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<String> sendAppointmentReminder(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentReminder(appointmentId);
            return ResponseEntity.ok("Appointment reminder sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send appointment reminder: " + e.getMessage());
        }
    }

    @PostMapping("/appointment-confirmation/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendAppointmentConfirmation(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentConfirmation(appointmentId);
            return ResponseEntity.ok("Appointment confirmation sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send appointment confirmation: " + e.getMessage());
        }
    }

    @PostMapping("/appointment-cancellation/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<String> sendAppointmentCancellation(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentCancellation(appointmentId);
            return ResponseEntity.ok("Appointment cancellation notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send cancellation notification: " + e.getMessage());
        }
    }

    @PostMapping("/appointment-reschedule/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<String> sendAppointmentReschedule(@PathVariable Long appointmentId) {
        try {
            notificationService.sendAppointmentReschedule(appointmentId);
            return ResponseEntity.ok("Appointment reschedule notification sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send reschedule notification: " + e.getMessage());
        }
    }

    @PostMapping("/daily-schedule/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendDailySchedule(@PathVariable Long doctorId) {
        try {
            notificationService.sendDailyAppointmentReminders();
            return ResponseEntity.ok("Daily schedule sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send daily schedule: " + e.getMessage());
        }
    }

    @PostMapping("/weekly-schedule/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendWeeklySchedule(@PathVariable Long doctorId) {
        try {
            notificationService.sendWeeklySchedule(doctorId);
            return ResponseEntity.ok("Weekly schedule sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send weekly schedule: " + e.getMessage());
        }
    }

    @PostMapping("/feedback-reminder/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> sendFeedbackReminder(@PathVariable Long appointmentId) {
        try {
            notificationService.sendFeedbackReminder(appointmentId);
            return ResponseEntity.ok("Feedback reminder sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to send feedback reminder: " + e.getMessage());
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
            return ResponseEntity.badRequest().body("Failed to send system notification: " + e.getMessage());
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
            return ResponseEntity.badRequest().body("Failed to send bulk notification: " + e.getMessage());
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
            return ResponseEntity.badRequest().body("Failed to send custom notification: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getNotificationStatus() {
        try {
            Map<String, Object> status = Map.of(
                "service", "Notification Service",
                "status", "Active",
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
} 