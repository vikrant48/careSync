package com.vikrant.careSync.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** ErrorLog finds the catch location by itself, keeps only the useful parts, and masks personal data. */
class ErrorLogTest {

    @Test
    void locationIsFoundAutomatically() {
        // Same walker option ErrorLog uses (class references are needed for the class name)
        Optional<StackWalker.StackFrame> here = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.findFirst());

        String location = ErrorLog.location(here);

        assertTrue(location.startsWith("[ErrorLogTest.java : ErrorLogTest : locationIsFoundAutomatically : line "),
                location);
    }

    @Test
    void describeShowsTypeMessageRootCauseAndWhereItWasThrown() {
        Exception error = new IllegalStateException("Booking failed", new RuntimeException("Slot already taken"));

        String text = ErrorLog.describe(error);

        assertTrue(text.startsWith("IllegalStateException: Booking failed"), text);
        assertTrue(text.contains("| cause: RuntimeException: Slot already taken"), text);
        assertTrue(text.contains("| thrown at ErrorLogTest.java:"), text);
    }

    @Test
    void personalDataInExceptionMessagesIsMasked() {
        String clean = ErrorLog.sanitize("Key (email)=(asha.rao@example.com) exists; phone 9876543210; token "
                + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.sig and Bearer abc.def\nnext line");

        assertFalse(clean.contains("asha.rao@example.com"));
        assertFalse(clean.contains("9876543210"));
        assertFalse(clean.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertFalse(clean.contains("abc.def"));
        assertFalse(clean.contains("\n"), "one line only");
        assertTrue(clean.contains("[email]") && clean.contains("[number]") && clean.contains("[token]"), clean);
    }

    @Test
    void longMessagesAreShortened() {
        assertTrue(ErrorLog.sanitize("x".repeat(1000)).length() <= 303);
    }

    @Test
    void levelFollowsTheKindOfError() {
        assertEquals(ErrorLog.Level.WARN, ErrorLog.levelFor(new IllegalArgumentException("Unknown specialization")));
        assertEquals(ErrorLog.Level.WARN, ErrorLog.levelFor(new AccessDeniedException("Access denied")));
        assertEquals(ErrorLog.Level.ERROR, ErrorLog.levelFor(new NullPointerException()));
        assertEquals(ErrorLog.Level.DEBUG, ErrorLog.levelFor(new io.jsonwebtoken.MalformedJwtException("bad")));
    }

    @Test
    void lambdaMethodNamesAreReadable() {
        assertEquals("bookAppointment (lambda)", ErrorLog.methodName("lambda$bookAppointment$3"));
        assertEquals("bookAppointment", ErrorLog.methodName("bookAppointment"));
    }

    @Test
    void loggingNeverThrows() {
        assertDoesNotThrow(() -> ErrorLog.log(new RuntimeException("boom")));
        assertDoesNotThrow(() -> ErrorLog.warn(null, "no exception given"));
        assertDoesNotThrow(() -> ErrorLog.error(new RuntimeException(), "context {} and {}", 1));
    }
}
