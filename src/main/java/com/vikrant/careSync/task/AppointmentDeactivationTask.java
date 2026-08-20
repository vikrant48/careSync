package com.vikrant.careSync.task;

import com.vikrant.careSync.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentDeactivationTask {

    private final AppointmentRepository appointmentRepository;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    /**
     * Runs every midnight to deactivate appointments that stayed in BOOKED status
     * or were cancelled today.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void deactivateExpiredAppointments() {
        if (!cleanupRunning.compareAndSet(false, true)) {
            log.warn("Skipping stale appointment cleanup because a previous run is still active");
            return;
        }

        log.info("Starting scheduled task: Deactivating stale BOOKED appointments at midnight");

        try {
            // Deactivate anything scheduled before today that is still BOOKED
            LocalDateTime threshold = LocalDate.now().atStartOfDay();
            int deactivatedCount = appointmentRepository.deactivateExpiredAppointments(threshold);

            if (deactivatedCount > 0) {
                log.info("Successfully deactivated {} stale appointments", deactivatedCount);
            } else {
                log.debug("No stale appointments found to deactivate");
            }
        } finally {
            cleanupRunning.set(false);
        }
    }
}
