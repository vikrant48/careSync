package com.vikrant.careSync.scheduler;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    /**
     * Runs every 1 minute to check for upcoming appointments needing 24h, 1h, or
     * 10m reminders.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processScheduledReminders() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Check 24-hour reminders (window: 23h55m to 24h05m)
        LocalDateTime window24hStart = now.plusHours(23).plusMinutes(55);
        LocalDateTime window24hEnd = now.plusHours(24).plusMinutes(5);
        List<Appointment> list24h = appointmentRepository.findAppointmentsNeeding24hReminder(window24hStart,
                window24hEnd);

        for (Appointment app : list24h) {
            try {
                notificationService.sendAppointmentConfirmation(app.getId());
                app.setReminder24hSent(true);
                appointmentRepository.save(app);
                log.info("Dispatched 24h appointment reminder for appointment ID [{}]", app.getId());
            } catch (Exception e) {
                log.error("Failed to send 24h reminder for appointment ID [{}]: {}", app.getId(), e.getMessage());
            }
        }

        // 2. Check 1-hour reminders (window: 55m to 65m)
        LocalDateTime window1hStart = now.plusMinutes(55);
        LocalDateTime window1hEnd = now.plusMinutes(65);
        List<Appointment> list1h = appointmentRepository.findAppointmentsNeeding1hReminder(window1hStart, window1hEnd);

        for (Appointment app : list1h) {
            try {
                notificationService.sendAppointmentScheduled(app.getId());
                app.setReminder1hSent(true);
                appointmentRepository.save(app);
                log.info("Dispatched 1h appointment reminder for appointment ID [{}]", app.getId());
            } catch (Exception e) {
                log.error("Failed to send 1h reminder for appointment ID [{}]: {}", app.getId(), e.getMessage());
            }
        }

        // 3. Check 10-minute reminders (window: 5m to 15m)
        LocalDateTime window10mStart = now.plusMinutes(5);
        LocalDateTime window10mEnd = now.plusMinutes(15);
        List<Appointment> list10m = appointmentRepository.findAppointmentsNeeding10mReminder(window10mStart,
                window10mEnd);

        for (Appointment app : list10m) {
            try {
                notificationService.sendAppointmentStarted(app.getId());
                app.setReminder10mSent(true);
                appointmentRepository.save(app);
                log.info("Dispatched 10m appointment reminder for appointment ID [{}]", app.getId());
            } catch (Exception e) {
                log.error("Failed to send 10m reminder for appointment ID [{}]: {}", app.getId(), e.getMessage());
            }
        }
    }
}
