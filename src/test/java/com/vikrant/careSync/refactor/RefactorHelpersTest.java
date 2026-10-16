package com.vikrant.careSync.refactor;

import com.vikrant.careSync.controller.support.DocumentUploads;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentStatusLog;
import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.util.ProfileCompletion;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Batch 18: the shared helpers keep the old results and messages. */
class RefactorHelpersTest {

    @Test
    void profileCompletionAddsCompleteSectionsOnly() {
        int score = new ProfileCompletion()
                .section(30, ProfileCompletion.filled("Asha", "Verma", "a@x", "9000"))
                .section(30, ProfileCompletion.filled("F", ""))
                .section(20, ProfileCompletion.filled((String) null))
                .section(20, true)
                .total();
        assertEquals(50, score);
    }

    @Test
    void statusLogFactoryKeepsEveryField() {
        AppointmentStatusLog log = AppointmentStatusLog.of(5L, Appointment.Status.BOOKED,
                Appointment.Status.CONFIRMED, "patientA", "Confirmed");
        assertEquals(5L, log.getAppointmentId());
        assertEquals(Appointment.Status.BOOKED, log.getPreviousStatus());
        assertEquals(Appointment.Status.CONFIRMED, log.getNewStatus());
        assertEquals("patientA", log.getChangedBy());
        assertEquals("Confirmed", log.getReason());
    }

    @Test
    void uploadsKeepTheirErrorMessages() {
        DocumentUploads uploads = new DocumentUploads(mock(UserService.class), mock(DocumentService.class));
        var auth = new UsernamePasswordAuthenticationToken("patientA", null);

        ResponseEntity<?> storage = uploads.handle(auth, "lab report", "Invalid file", (u, t) -> {
            throw new IOException("bucket unreachable");
        });
        assertEquals(400, storage.getStatusCode().value());
        assertTrue(String.valueOf(storage.getBody()).startsWith("Failed to upload lab report: "));

        ResponseEntity<?> invalid = uploads.handle(auth, "lab report", "Invalid file", (u, t) -> {
            throw new IllegalArgumentException("The file content does not match its .pdf extension");
        });
        assertEquals("Invalid file: The file content does not match its .pdf extension", invalid.getBody());

        ResponseEntity<?> ok = uploads.handle(auth, "lab report", "Invalid file",
                (u, t) -> Map.of("uploadedBy", u, "role", t));
        assertEquals(Map.of("uploadedBy", "patientA", "role", "UNKNOWN"), ok.getBody());
    }
}
