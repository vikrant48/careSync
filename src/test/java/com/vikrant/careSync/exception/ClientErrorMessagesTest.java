package com.vikrant.careSync.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Users see messages written for them; database, storage and programming errors stay in the log. */
class ClientErrorMessagesTest {

    @Test
    void userFacingMessagesPassThrough() {
        assertEquals("Doctor is not available on this date",
                ClientErrorMessages.of(new RuntimeException("Doctor is not available on this date")));
        assertEquals("Invalid or expired OTP", ClientErrorMessages.of(new IllegalStateException("Invalid or expired OTP")));
    }

    @Test
    void databaseErrorsAreHidden() {
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new DataIntegrityViolationException(
                "could not execute statement; constraint [uk_users_email]")));
        assertEquals(ClientErrorMessages.GENERIC,
                ClientErrorMessages.of(new RuntimeException("Save failed", new SQLException("relation \"users\""))));
    }

    @Test
    void wrappedInternalTextIsHidden() {
        // Services often wrap a cause's message into their own text without keeping the cause
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new RuntimeException(
                "Payment failed: ERROR: duplicate key value violates unique constraint \"payments_pkey\"")));
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new RuntimeException(
                "Upload failed: software.amazon.awssdk.services.s3.model.S3Exception: Access Denied")));
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new RuntimeException(
                "Failed: https://internal-storage.example/bucket/patient-file.pdf")));
    }

    @Test
    void programmingAndIoErrorsAreHidden() {
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new NullPointerException(
                "Cannot invoke \"getId()\" because \"patient\" is null")));
        assertEquals(ClientErrorMessages.GENERIC,
                ClientErrorMessages.of(new RuntimeException("x", new IOException("disk"))));
    }

    @Test
    void emptyOrMissingMessagesBecomeGeneric() {
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new RuntimeException()));
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(null));
        assertEquals(ClientErrorMessages.GENERIC, ClientErrorMessages.of(new RuntimeException("x".repeat(400))));
    }
}
