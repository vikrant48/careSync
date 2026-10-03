package com.vikrant.careSync.scheduler;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentStatusLog;
import com.vikrant.careSync.event.AppointmentNoShowEvent;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.AppointmentStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentCleanupScheduler {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusLogRepository statusLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Runs every 15 minutes to detect unstarted appointments and orphaned rooms.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void cleanupUnstartedAndOrphanedAppointments() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Process unstarted past appointments (past by > 15 minutes)
        LocalDateTime cutoffUnstarted = now.minusMinutes(15);
        List<Appointment> unstartedList = appointmentRepository.findUnstartedPastAppointments(cutoffUnstarted);

        for (Appointment app : unstartedList) {
            try {
                Appointment.Status previousStatus = app.getStatus();
                app.changeStatus(Appointment.Status.NO_SHOW_PATIENT, "SYSTEM_NO_SHOW_SCHEDULER");
                appointmentRepository.save(app);

                // Audit log
                statusLogRepository.save(AppointmentStatusLog.builder()
                        .appointmentId(app.getId())
                        .previousStatus(previousStatus)
                        .newStatus(Appointment.Status.NO_SHOW_PATIENT)
                        .changedBy("SYSTEM_SCHEDULER")
                        .reason("Patient failed to check in or enter consultation within 15 minutes of scheduled time")
                        .build());

                // Domain Event
                eventPublisher.publishEvent(new AppointmentNoShowEvent(
                        this,
                        app.getId(),
                        app.getDoctor().getId(),
                        app.getPatient().getId(),
                        Appointment.Status.NO_SHOW_PATIENT));

                log.info("Auto-marked appointment ID [{}] as NO_SHOW_PATIENT", app.getId());
            } catch (Exception e) {
                log.error("Failed to auto-process no-show for appointment ID [{}]: {}", app.getId(), e.getMessage());
            }
        }

        // 2. Process orphaned IN_PROGRESS appointments (stuck for > 30 minutes without
        // completion)
        LocalDateTime cutoffOrphaned = now.minusMinutes(30);
        List<Appointment> orphanedList = appointmentRepository.findOrphanedInProgressAppointments(cutoffOrphaned);

        for (Appointment app : orphanedList) {
            try {
                Appointment.Status previousStatus = app.getStatus();
                app.changeStatus(Appointment.Status.REPORT_DRAFTED, "SYSTEM_ORPHAN_CLEANUP_SCHEDULER");
                appointmentRepository.save(app);

                statusLogRepository.save(AppointmentStatusLog.builder()
                        .appointmentId(app.getId())
                        .previousStatus(previousStatus)
                        .newStatus(Appointment.Status.REPORT_DRAFTED)
                        .changedBy("SYSTEM_SCHEDULER")
                        .reason("Video session ended or inactive for over 30 minutes. Status transitioned to REPORT_DRAFTED pending doctor sign-off.")
                        .build());

                log.info("Auto-transitioned orphaned IN_PROGRESS appointment ID [{}] to REPORT_DRAFTED", app.getId());
            } catch (Exception e) {
                log.error("Failed to process orphaned appointment ID [{}]: {}", app.getId(), e.getMessage());
            }
        }
    }
}
