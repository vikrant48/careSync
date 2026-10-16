package com.vikrant.careSync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.ChatMessageRequest;
import com.vikrant.careSync.entity.ChatMessage;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.ChatRepository;
import com.vikrant.careSync.security.CustomUserDetailsService;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.security.websocket.WebSocketAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatControllerTest {

    private ChatRepository chatRepository;
    private SimpMessagingTemplate messagingTemplate;
    private AccessGuard accessGuard;
    private CurrentUserService currentUserService;
    private ChatController controller;
    private final Principal patientA = new UsernamePasswordAuthenticationToken(
            "patientA", null, AuthorityUtils.createAuthorityList("ROLE_PATIENT"));

    @BeforeEach
    void setUp() {
        chatRepository = mock(ChatRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        accessGuard = mock(AccessGuard.class);
        currentUserService = mock(CurrentUserService.class);
        WebSocketAuthentication webSocketAuthentication = new WebSocketAuthentication(
                mock(JwtService.class), mock(CustomUserDetailsService.class),
                mock(com.vikrant.careSync.security.service.SecurityService.class));
        controller = new ChatController(chatRepository, messagingTemplate, webSocketAuthentication, accessGuard,
                currentUserService);
        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUserService.require()).thenReturn(new AuthenticatedUser(201L, "patientA", User.Role.PATIENT));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ChatMessageRequest request(Long appointmentId, String content) {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setAppointmentId(appointmentId);
        request.setContent(content);
        return request;
    }

    @Test
    void senderIdentityComesFromSessionNotFromPayload() throws Exception {
        when(accessGuard.canAccessAppointment(500L)).thenReturn(true);
        // A client trying to impersonate the doctor: these fields must be ignored
        ChatMessageRequest spoofed = new ObjectMapper().readValue(
                "{\"appointmentId\":500,\"content\":\"hello\",\"senderRole\":\"DOCTOR\",\"senderId\":101,\"id\":1}",
                ChatMessageRequest.class);

        controller.sendMessage(spoofed, patientA);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepository).save(saved.capture());
        assertNull(saved.getValue().getId(), "client must not choose the row id (would overwrite messages)");
        assertEquals("PATIENT", saved.getValue().getSenderRole());
        assertEquals(201L, saved.getValue().getSenderId());
        assertEquals("hello", saved.getValue().getContent());
        verify(messagingTemplate).convertAndSend(eq("/topic/appointment/500"), any(Object.class));
    }

    @Test
    void nonParticipantCannotPost() {
        when(accessGuard.canAccessAppointment(501L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.sendMessage(request(501L, "hi"), patientA));
        verifyNoInteractions(chatRepository, messagingTemplate);
    }

    @Test
    void anonymousSessionCannotPost() {
        assertThrows(AccessDeniedException.class, () -> controller.sendMessage(request(500L, "hi"), null));
        verifyNoInteractions(chatRepository, messagingTemplate);
    }

    @Test
    void blankOrTooLongMessagesAreRejected() {
        when(accessGuard.canAccessAppointment(500L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(request(500L, "   "), patientA));
        String tooLong = "x".repeat(ChatController.MAX_CONTENT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(request(500L, tooLong), patientA));
        verifyNoInteractions(chatRepository);
    }

    @Test
    void securityContextIsRestoredAfterHandling() {
        when(accessGuard.canAccessAppointment(500L)).thenReturn(true);
        controller.sendMessage(request(500L, "hi"), patientA);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
