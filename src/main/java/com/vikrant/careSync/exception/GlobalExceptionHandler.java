package com.vikrant.careSync.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
            error.put("error", "Invalid request");
            error.put("message", message != null ? message : "An invalid argument was provided.");
            error.put("code", "INVALID_ARGUMENT");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
