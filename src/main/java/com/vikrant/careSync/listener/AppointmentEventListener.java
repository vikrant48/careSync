package com.vikrant.careSync.listener;

import com.vikrant.careSync.event.*;
import com.vikrant.careSync.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleAppointmentBooked(AppointmentBookedEvent event) {
        log.info("Handling AppointmentBookedEvent for appointment ID [{}] (Emergency: {})",
                event.getAppointmentId(), event.isEmergency());
        try {
            notificationService.sendDoctorNewAppointmentNotification(event.getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process AppointmentBookedEvent notification: {}", e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handlePatientCheckedIn(PatientCheckedInEvent event) {
        log.info("Handling PatientCheckedInEvent for appointment ID [{}]", event.getAppointmentId());
        try {
            notificationService.sendAppointmentStarted(event.getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process PatientCheckedInEvent notification: {}", e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handleVisitStarted(VisitStartedEvent event) {
        log.info("Handling VisitStartedEvent for appointment ID [{}]", event.getAppointmentId());
        try {
            notificationService.sendAppointmentStarted(event.getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process VisitStartedEvent notification: {}", e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handleMedicalReportSigned(MedicalReportSignedEvent event) {
        log.info("Handling MedicalReportSignedEvent for appointment ID [{}]", event.getAppointmentId());
        try {
            notificationService.sendAppointmentCompleted(event.getAppointmentId());
            notificationService.sendFeedbackReminder(event.getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process MedicalReportSignedEvent notification: {}", e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handleAppointmentNoShow(AppointmentNoShowEvent event) {
        log.info("Handling AppointmentNoShowEvent for appointment ID [{}] status [{}]",
                event.getAppointmentId(), event.getNoShowStatus());
        try {
            notificationService.sendAppointmentCancellation(event.getAppointmentId());
        } catch (Exception e) {
            log.error("Failed to process AppointmentNoShowEvent notification: {}", e.getMessage(), e);
        }
    }
}
