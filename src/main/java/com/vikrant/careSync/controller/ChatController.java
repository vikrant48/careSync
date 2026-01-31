package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.ChatMessage;
import com.vikrant.careSync.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRepository chatRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        ChatMessage saved = chatRepository.save(chatMessage);

        // Broadcast to specific appointment topic
        messagingTemplate.convertAndSend("/topic/appointment/" + chatMessage.getAppointmentId(), saved);
    }

    @GetMapping("/api/chat/{appointmentId}")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@PathVariable Long appointmentId) {
        return chatRepository.findByAppointmentIdOrderByTimestampAsc(appointmentId);
    }
}
