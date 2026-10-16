package com.vikrant.careSync.security.websocket;

import com.vikrant.careSync.security.access.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.security.Principal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StompAuthChannelInterceptorTest {

    private final MessageChannel channel = mock(MessageChannel.class);
    private WebSocketAuthentication webSocketAuthentication;
    private AccessGuard accessGuard;
    private StompAuthChannelInterceptor interceptor;
    private final Authentication patientA = new UsernamePasswordAuthenticationToken(
            "patientA", null, AuthorityUtils.createAuthorityList("ROLE_PATIENT"));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        webSocketAuthentication = mock(WebSocketAuthentication.class);
        accessGuard = mock(AccessGuard.class);
        interceptor = new StompAuthChannelInterceptor(webSocketAuthentication, accessGuard);
        when(webSocketAuthentication.runAs(any(), any()))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(1)).get());
        when(webSocketAuthentication.authenticate("Bearer good")).thenReturn(Optional.of(patientA));
        when(accessGuard.canAccessAppointment(500L)).thenReturn(true);
        when(accessGuard.canAccessAppointment(501L)).thenReturn(false);
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal user, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal userOf(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser();
    }

    @Test
    void connectWithValidTokenAuthenticatesSession() {
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer good"), channel);
        assertSame(patientA, userOf(result));
    }

    @Test
    void connectWithoutOrWithBadTokenStaysAnonymous() {
        Message<?> noToken = interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel);
        Message<?> badToken = interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer forged"), channel);
        assertNotNull(noToken);
        assertNull(userOf(noToken));
        assertNotNull(badToken);
        assertNull(userOf(badToken));
    }

    @Test
    void anonymousCannotSubscribeToAppointmentChat() {
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/appointment/500", null, null), channel));
        verifyNoInteractions(accessGuard);
    }

    @Test
    void participantCanSubscribeButOthersCannot() {
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/appointment/500", patientA, null), channel));
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/appointment/501", patientA, null), channel));
        verify(webSocketAuthentication, times(2)).runAs(eq(patientA), any());
    }

    @Test
    void openDestinationsAllowedForEveryone() {
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", null, null), channel));
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/appointment-updates", null, null), channel));
    }

    @Test
    void unknownOrMalformedDestinationsAreDenied() {
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/anything", patientA, null), channel));
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/appointment/500/x", patientA, null), channel));
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/otherUser/queue/notifications", patientA, null), channel));
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, patientA, null), channel));
    }

    @Test
    void sendRequiresAuthenticatedSession() {
        assertNull(interceptor.preSend(frame(StompCommand.SEND, "/app/chat.sendMessage", null, null), channel));
        assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "/app/chat.sendMessage", patientA, null), channel));
    }
}
