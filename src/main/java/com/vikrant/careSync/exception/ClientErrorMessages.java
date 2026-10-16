package com.vikrant.careSync.exception;

import jakarta.persistence.PersistenceException;
import org.hibernate.HibernateException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which exception text may be sent to API clients. Messages written for users
 * ("Doctor is not available on this date") pass through; anything from the database, storage,
 * outside services or programming errors is replaced by a generic message (the caller logs the detail).
 */
public final class ClientErrorMessages {

    public static final String GENERIC = "Something went wrong while processing your request. Please try again.";

    private static final int MAX_LENGTH = 300;

    // Infrastructure and programming errors: their text describes internals, never the user's mistake
    private static final List<Class<? extends Throwable>> INTERNAL_TYPES = List.of(
            DataAccessException.class, SQLException.class, PersistenceException.class, HibernateException.class,
            TransactionException.class, IOException.class, SdkException.class, RestClientException.class,
            NullPointerException.class, ClassCastException.class, IndexOutOfBoundsException.class,
            ArithmeticException.class);

    // Text that reveals SQL, class names, stack frames, file paths or internal URLs
    private static final Pattern INTERNAL_TEXT = Pattern.compile("(?i)("
            + "\\bsql|jdbc|hibernate|\\bconstraint\\b|duplicate key|violates|could not (execute|extract|prepare)"
            + "|\\b(org|java|javax|jakarta|com|io|software|okhttp3)\\.[a-z]+\\."
            + "|exception|stack ?trace|\\bat [\\w$.]+\\("
            + "|\\bselect\\b.+\\bfrom\\b|\\binsert into\\b|\\bupdate\\s+\\w+\\s+set\\b|\\bdelete from\\b"
            + "|relation \"|column \"|\\berror:"
            + "|https?://|[a-z]:\\\\|/(home|usr|var|tmp|app|opt)/)");

    private ClientErrorMessages() {
    }

    /** The exception's own message when it is safe to show, otherwise {@link #GENERIC}. */
    public static String of(Throwable error) {
        return isSafeToShow(error) ? error.getMessage().trim() : GENERIC;
    }

    /** The usual controller error body: {"error": safe message}. */
    public static java.util.Map<String, String> errorBody(Throwable error) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("error", of(error));
        return body;
    }

    /** Same body typed for endpoints that declare Map<String, Object>. */
    public static java.util.Map<String, Object> errorBodyObject(Throwable error) {
        return new java.util.HashMap<>(errorBody(error));
    }

    /** {"error": ..., "message": ...}: both keys carry the safe message (some frontend alerts read "message"). */
    public static java.util.Map<String, String> errorAndMessageBody(Throwable error) {
        java.util.Map<String, String> body = errorBody(error);
        body.put("message", body.get("error"));
        return body;
    }

    public static boolean isSafeToShow(Throwable error) {
        if (error == null) {
            return false;
        }
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            for (Class<? extends Throwable> type : INTERNAL_TYPES) {
                if (type.isInstance(current)) {
                    return false;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        String message = error.getMessage();
        return message != null && !message.isBlank() && message.length() <= MAX_LENGTH
                && !INTERNAL_TEXT.matcher(message).find();
    }
}
