package com.vikrant.careSync.util;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.service.ai.AiQuotaExceededException;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One log line per caught exception, in one format:
 * <pre>
 * [AppointmentService.java : AppointmentService : bookAppointment : line 120] Booking failed
 *     -> IllegalStateException: Slot already taken | cause: PSQLException: ... | thrown at SlotLockService.java:49 (acquireSlotLock)
 * </pre>
 * File, class, method and line of the catch block are found automatically (StackWalker), never written by hand.
 * Only the useful parts of the exception are logged: its type and message, the root cause, and the first place
 * in our code where it was thrown. Full stack traces only when the "careSync.stacktrace" logger is at DEBUG
 * (LOG_STACKTRACES=DEBUG).
 *
 * Exception messages can contain user data (database errors quote values), so emails, long numbers and tokens in
 * them are masked. The context message is written by the developer and logged as given.
 *
 * Use {@link #log(Throwable)} in a catch block to pick the level from the exception: DEBUG for rejected tokens,
 * WARN for expected problems (bad input, access denied, a business rule with a message meant for the user), ERROR
 * for everything else. The level methods (error, warn, info, debug) keep a level chosen on purpose.
 */
public final class ErrorLog {

    private static final String APP_PACKAGE = "com.vikrant.careSync.";
    private static final Logger STACKTRACES = LoggerFactory.getLogger("careSync.stacktrace");
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final int MAX_MESSAGE = 300;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{7,}");

    enum Level {
        ERROR, WARN, INFO, DEBUG
    }

    private ErrorLog() {
    }

    // ---------------------------------------------------------------- level from the exception

    public static void log(Throwable error) {
        write(levelFor(error), error, null);
    }

    public static void log(Throwable error, String message, Object... args) {
        write(levelFor(error), error, format(message, args));
    }

    // ---------------------------------------------------------------- level chosen by the caller

    public static void error(Throwable error) {
        write(Level.ERROR, error, null);
    }

    public static void error(Throwable error, String message, Object... args) {
        write(Level.ERROR, error, format(message, args));
    }

    public static void warn(Throwable error) {
        write(Level.WARN, error, null);
    }

    public static void warn(Throwable error, String message, Object... args) {
        write(Level.WARN, error, format(message, args));
    }

    public static void info(Throwable error) {
        write(Level.INFO, error, null);
    }

    public static void info(Throwable error, String message, Object... args) {
        write(Level.INFO, error, format(message, args));
    }

    /** For exceptions that are part of normal flow (probes, fallbacks): visible only when debugging */
    public static void debug(Throwable error) {
        write(Level.DEBUG, error, null);
    }

    public static void debug(Throwable error, String message, Object... args) {
        write(Level.DEBUG, error, format(message, args));
    }

    // ---------------------------------------------------------------- internals

    static Level levelFor(Throwable error) {
        if (error instanceof JwtException) {
            return Level.DEBUG; // expired or malformed tokens happen all the time
        }
        if (error instanceof ResponseStatusException status) {
            return status.getStatusCode().is4xxClientError() ? Level.WARN : Level.ERROR;
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                || error instanceof AccessDeniedException || error instanceof AuthenticationException
                || error instanceof NoSuchElementException || error instanceof DateTimeException
                || error instanceof AiQuotaExceededException || error instanceof DataIntegrityViolationException
                || error instanceof ConstraintViolationException || error instanceof MethodArgumentNotValidException
                || ClientErrorMessages.isSafeToShow(error)) {
            return Level.WARN;
        }
        return Level.ERROR;
    }

    private static void write(Level level, Throwable error, String context) {
        Optional<StackWalker.StackFrame> caller = WALKER.walk(frames -> frames
                .filter(f -> f.getDeclaringClass() != ErrorLog.class)
                .findFirst());
        Class<?> owner = caller.isPresent() ? topLevel(caller.get().getDeclaringClass()) : ErrorLog.class;
        Logger logger = LoggerFactory.getLogger(owner);
        if (!isEnabled(logger, level)) {
            return;
        }
        String line = location(caller) + (context == null || context.isBlank() ? "" : " " + context.trim())
                + " -> " + describe(error);
        switch (level) {
            case ERROR -> logger.error(line);
            case WARN -> logger.warn(line);
            case INFO -> logger.info(line);
            case DEBUG -> logger.debug(line);
        }
        if (error != null && STACKTRACES.isDebugEnabled()) {
            STACKTRACES.debug(location(caller) + " full stack trace", error);
        }
    }

    /** "[File.java : Class : method : line N]" of the code that caught the exception */
    static String location(Optional<StackWalker.StackFrame> caller) {
        if (caller.isEmpty()) {
            return "[unknown]";
        }
        StackWalker.StackFrame frame = caller.get();
        return "[" + (frame.getFileName() != null ? frame.getFileName() : "?") + " : "
                + className(frame.getDeclaringClass()) + " : " + methodName(frame.getMethodName()) + " : line "
                + frame.getLineNumber() + "]";
    }

    /** Type and message, the root cause, and where in our code it was thrown */
    static String describe(Throwable error) {
        if (error == null) {
            return "(no exception)";
        }
        StringBuilder text = new StringBuilder(typeAndMessage(error));
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != error) {
            text.append(" | cause: ").append(typeAndMessage(root));
        }
        StackTraceElement origin = firstAppFrame(root);
        if (origin == null && root != error) {
            origin = firstAppFrame(error);
        }
        if (origin != null) {
            text.append(" | thrown at ").append(origin.getFileName()).append(':').append(origin.getLineNumber())
                    .append(" (").append(methodName(origin.getMethodName())).append(')');
        }
        return text.toString();
    }

    private static String typeAndMessage(Throwable error) {
        String message = sanitize(error.getMessage());
        return error.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }

    private static StackTraceElement firstAppFrame(Throwable error) {
        for (StackTraceElement element : error.getStackTrace()) {
            if (element.getClassName().startsWith(APP_PACKAGE) && !element.getClassName().equals(ErrorLog.class.getName())
                    && !element.getClassName().contains("$$")) {
                return element;
            }
        }
        return null;
    }

    /** Masks emails, tokens and long numbers (phones, card numbers) in exception text; one line, at most 300 chars */
    static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String clean = message.replaceAll("\\s+", " ").trim();
        clean = JWT.matcher(clean).replaceAll("[token]");
        clean = BEARER.matcher(clean).replaceAll("Bearer [token]");
        clean = EMAIL.matcher(clean).replaceAll("[email]");
        clean = LONG_NUMBER.matcher(clean).replaceAll("[number]");
        return clean.length() <= MAX_MESSAGE ? clean : clean.substring(0, MAX_MESSAGE) + "...";
    }

    private static String format(String message, Object[] args) {
        if (message == null) {
            return null;
        }
        return args == null || args.length == 0 ? message : MessageFormatter.arrayFormat(message, args).getMessage();
    }

    /** "lambda$bookAppointment$3" reads as "bookAppointment (lambda)" */
    static String methodName(String name) {
        if (name != null && name.startsWith("lambda$")) {
            String[] parts = name.split("\\$");
            return (parts.length > 1 ? parts[1] : name) + " (lambda)";
        }
        return name;
    }

    private static String className(Class<?> type) {
        String simple = type.getSimpleName();
        if (!simple.isEmpty()) {
            return type.getEnclosingClass() != null ? className(type.getEnclosingClass()) + "." + simple : simple;
        }
        // Anonymous class: name it after the class that contains it
        return type.getEnclosingClass() != null ? className(type.getEnclosingClass()) + " (anonymous)" : type.getName();
    }

    private static Class<?> topLevel(Class<?> type) {
        Class<?> top = type;
        while (top.getEnclosingClass() != null) {
            top = top.getEnclosingClass();
        }
        return top;
    }

    private static boolean isEnabled(Logger logger, Level level) {
        return switch (level) {
            case ERROR -> logger.isErrorEnabled();
            case WARN -> logger.isWarnEnabled();
            case INFO -> logger.isInfoEnabled();
            case DEBUG -> logger.isDebugEnabled();
        };
    }
}
