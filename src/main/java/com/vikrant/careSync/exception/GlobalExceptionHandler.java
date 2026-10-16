package com.vikrant.careSync.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Role or ownership check failed (@PreAuthorize or AccessGuard). Without this handler the
     * generic RuntimeException handler below would turn it into a 400.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(forbiddenBody());
    }

    public static Map<String, String> forbiddenBody() {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Access denied");
        error.put("message", "You do not have permission to access this resource.");
        error.put("code", "FORBIDDEN");
        return error;
    }

    /**
     * Handles file upload size exceeded errors.
     * Returns a 413 Payload Too Large with a clear user-facing message.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "File too large");
        error.put("message",
                "The file you are trying to upload exceeds the maximum allowed size of 5 MB. Please choose a smaller file.");
        error.put("code", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Handles unsupported file type errors (thrown from service layer).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        String message = ex.getMessage();

        if (message != null && message.toLowerCase().contains("extension")) {
            error.put("error", "Unsupported file type");
            error.put("message", "This file type is not supported. Allowed types: JPG, JPEG, PNG, PDF, DOC, DOCX.");
            error.put("code", "UNSUPPORTED_FILE_TYPE");
        } else {
            logIfHidden(ex);
            error.put("error", "Invalid request");
            error.put("message", ClientErrorMessages.isSafeToShow(ex) ? ClientErrorMessages.of(ex)
                    : "An invalid argument was provided.");
            error.put("code", "INVALID_ARGUMENT");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /** Request body is not valid JSON or has a wrong field type; the parser text names internal classes. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("Invalid request", "The request body is malformed or has a field of the wrong type.",
                        "MALFORMED_REQUEST"));
    }

    /** A unique key or foreign key was violated. Database text (table, column, values) stays in the log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("Request Failed", "This change conflicts with existing data.", "CONFLICT"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(DataAccessException ex) {
        log.error("Database error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("Request Failed", ClientErrorMessages.GENERIC, "INTERNAL_ERROR"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null && ClientErrorMessages.isSafeToShow(new RuntimeException(ex.getReason()))
                ? ex.getReason()
                : ClientErrorMessages.GENERIC;
        return ResponseEntity.status(ex.getStatusCode()).body(body("Request Failed", reason, "REQUEST_FAILED"));
    }

    /**
     * Handles generic runtime exceptions across the application.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        logIfHidden(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("Request Failed", ClientErrorMessages.of(ex), "BAD_REQUEST"));
    }

    private static Map<String, String> body(String error, String message, String code) {
        Map<String, String> body = new HashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("code", code);
        return body;
    }

    // The client only sees the generic text, so keep the real cause for support
    private static void logIfHidden(Exception ex) {
        if (!ClientErrorMessages.isSafeToShow(ex)) {
            log.error("Request failed with an internal error", ex);
        }
    }
}
