package com.vikrant.careSync.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.vikrant.careSync.dto.ChatMessageRequest;
import com.vikrant.careSync.entity.ChatMessage;
import com.vikrant.careSync.repository.ChatRepository;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.security.websocket.WebSocketAuthentication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    // chat_messages.content is varchar(255)
    static final int MAX_CONTENT_LENGTH = 255;

    private final ChatRepository chatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketAuthentication webSocketAuthentication;
    private final AccessGuard accessGuard;
    private final CurrentUserService currentUserService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageRequest request, Principal principal) {
        if (request == null || request.getAppointmentId() == null) {
            throw new IllegalArgumentException("appointmentId is required");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Message must be 1-" + MAX_CONTENT_LENGTH + " characters");
        }

        // Sender identity comes from the session, never from the message body
        AuthenticatedUser sender = webSocketAuthentication.runAs(principal, () -> {
            if (!accessGuard.canAccessAppointment(request.getAppointmentId())) {
                throw new AccessDeniedException("Not a participant of this appointment");
            }
            return currentUserService.require();
        });

        ChatMessage message = ChatMessage.builder()
                .appointmentId(request.getAppointmentId())
                .content(content)
                .senderRole(sender.role().name())
                .senderId(sender.id())
                .timestamp(LocalDateTime.now())
                .build();
        ChatMessage saved = chatRepository.save(message);

        // Broadcast to specific appointment topic
        messagingTemplate.convertAndSend("/topic/appointment/" + saved.getAppointmentId(), saved);
    }

    @MessageExceptionHandler
    public void handleChatError(Exception e, Principal principal) {
        log.warn("Chat message rejected for [{}]: {}", principal != null ? principal.getName() : "anonymous",
                e.getMessage());
    }

    @GetMapping("/api/chat/{appointmentId}")
    @PreAuthorize("@access.canAccessAppointment(#appointmentId)")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@PathVariable Long appointmentId) {
        return chatRepository.findByAppointmentIdOrderByTimestampAsc(appointmentId);
    }
}
