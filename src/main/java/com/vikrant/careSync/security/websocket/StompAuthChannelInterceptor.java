package com.vikrant.careSync.security.websocket;

import com.vikrant.careSync.security.access.AccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP sessions and authorizes subscriptions and sends.
 *
 * CONNECT: an "Authorization: Bearer <jwt>" header logs the session in. Without a valid token the
 * session stays anonymous (so logged-out pages do not loop on reconnect), but anonymous sessions
 * can only use the harmless destinations below.
 * SUBSCRIBE: /topic/appointment/{id} needs a logged-in participant of that appointment.
 * SEND: needs a logged-in session; ChatController checks the appointment.
 * Denied frames are dropped (not answered with ERROR, which would close the whole connection).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern APPOINTMENT_TOPIC = Pattern.compile("^/topic/appointment/(\\d+)$");

    /** Destinations without per-user data: per-user queues only ever carry the session user's messages. */
    private static final Set<String> OPEN_SUBSCRIPTIONS = Set.of(
            "/user/queue/notifications",
            "/topic/appointment-updates");

    private final WebSocketAuthentication webSocketAuthentication;
    private final AccessGuard accessGuard;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            webSocketAuthentication.authenticate(accessor.getFirstNativeHeader("Authorization"))
                    .ifPresent(accessor::setUser);
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            return isSubscriptionAllowed(accessor.getDestination(), accessor.getUser()) ? message : deny(accessor);
        }
        if (command == StompCommand.SEND) {
            return accessor.getUser() != null ? message : deny(accessor);
        }
        return message;
    }

    boolean isSubscriptionAllowed(String destination, Principal user) {
        if (destination == null) {
            return false;
        }
        if (OPEN_SUBSCRIPTIONS.contains(destination)) {
            return true;
        }
        Matcher matcher = APPOINTMENT_TOPIC.matcher(destination);
        if (matcher.matches() && user != null) {
            Long appointmentId = Long.valueOf(matcher.group(1));
            return webSocketAuthentication.runAs(user, () -> accessGuard.canAccessAppointment(appointmentId));
        }
        return false;
    }

    private Message<?> deny(StompHeaderAccessor accessor) {
        log.warn("Denied STOMP {} to [{}] for session user [{}]", accessor.getCommand(), accessor.getDestination(),
                accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");
        return null;
    }
}
