package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.AiConversationDtos;
import com.vikrant.careSync.dto.AiEmergencyInfo;
import com.vikrant.careSync.entity.AiConversation;
import com.vikrant.careSync.entity.AiMessage;
import com.vikrant.careSync.repository.AiConversationRepository;
import com.vikrant.careSync.repository.AiMessageRepository;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saved AI chats (server side, so they follow the user to every device). Everything is stored encrypted and is
 * only ever read or changed for the logged-in user: another user's conversation id is treated as unknown.
 * Recording a turn never breaks the chat itself: failures are logged and the answer is still returned.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiChatHistoryService {

    static final int MAX_CONVERSATIONS_LISTED = 50;
    /** Very long chats stop being saved (the chat itself keeps working) */
    static final int MAX_MESSAGES_PER_CONVERSATION = 400;
    private static final int TITLE_LENGTH = 60;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * Saves one chat turn: the user's typed text (button clicks are not text) and the assistant's reply with its
     * card and emergency notice. Returns the conversation id to use from now on: the given one, or a new one when
     * that id belongs to someone else.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(String conversationId, String userText, AiChatResponse reply) {
        try {
            Long userId = currentUserId();
            if (userId == null || reply == null || reply.getResponse() == null) {
                return conversationId;
            }
            AiConversation conversation = conversationRepository.findByPublicId(conversationId).orElse(null);
            String id = conversationId;
            if (conversation != null && !conversation.getUserId().equals(userId)) {
                // Never add to (or reveal) someone else's chat
                id = UUID.randomUUID().toString();
                conversation = null;
            }
            LocalDateTime now = LocalDateTime.now();
            boolean typed = userText != null && !userText.isBlank() && !userText.startsWith("ACTION_");
            if (conversation == null) {
                conversation = new AiConversation();
                conversation.setPublicId(id);
                conversation.setUserId(userId);
                conversation.setTitle(title(typed ? userText : reply.getResponse()));
                conversation.setCreatedAt(now);
            }
            if (conversation.getMessageCount() >= MAX_MESSAGES_PER_CONVERSATION) {
                return id;
            }
            conversation.setUpdatedAt(now);
            conversation.setMessageCount(conversation.getMessageCount() + (typed ? 2 : 1));
            conversation = conversationRepository.save(conversation);

            if (typed) {
                messageRepository.save(message(conversation, "user", userText.trim(), null, null, now));
            }
            messageRepository.save(message(conversation, "assistant", reply.getResponse(),
                    json(reply.getSuggestion()), json(reply.getEmergency()), now));
            return id;
        } catch (Exception e) {
            log.warn("Could not save AI chat history: {}", e.getClass().getSimpleName());
            return conversationId;
        }
    }

    @Transactional(readOnly = true)
    public List<AiConversationDtos.Summary> list() {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(requireUserId(),
                        PageRequest.of(0, MAX_CONVERSATIONS_LISTED)).stream()
                .map(c -> new AiConversationDtos.Summary(c.getPublicId(), c.getTitle(), c.getUpdatedAt(),
                        c.getMessageCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiConversationDtos.Conversation get(String conversationId) {
        AiConversation conversation = own(conversationId);
        List<AiConversationDtos.Message> messages = messageRepository
                .findByConversationIdOrderByIdAsc(conversation.getId()).stream()
                .map(m -> new AiConversationDtos.Message(m.getRole(), m.getContent(),
                        read(m.getCardJson(), AiBookingSuggestion.class),
                        read(m.getEmergencyJson(), AiEmergencyInfo.class), m.getCreatedAt()))
                .toList();
        return new AiConversationDtos.Conversation(conversation.getPublicId(), conversation.getTitle(),
                conversation.getUpdatedAt(), messages);
    }

    @Transactional
    public AiConversationDtos.Summary rename(String conversationId, String title) {
        AiConversation conversation = own(conversationId);
        conversation.setTitle(title(title));
        conversation = conversationRepository.save(conversation);
        return new AiConversationDtos.Summary(conversation.getPublicId(), conversation.getTitle(),
                conversation.getUpdatedAt(), conversation.getMessageCount());
    }

    @Transactional
    public void delete(String conversationId) {
        conversationRepository.delete(own(conversationId));
    }

    @Transactional
    public int deleteAll() {
        return conversationRepository.deleteByUserId(requireUserId());
    }

    /** Retention job: chats not used for the given number of days */
    @Transactional
    public int purgeNotUsedSince(LocalDateTime cutoff) {
        return conversationRepository.deleteNotUpdatedSince(cutoff);
    }

    /** For "Download my data": every chat with its messages (text only) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportFor(Long userId) {
        List<Map<String, Object>> chats = new ArrayList<>();
        for (AiConversation c : conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)) {
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("title", c.getTitle());
            chat.put("startedAt", c.getCreatedAt());
            chat.put("lastMessageAt", c.getUpdatedAt());
            chat.put("messages", messageRepository.findByConversationIdOrderByIdAsc(c.getId()).stream()
                    .map(m -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("from", "user".equals(m.getRole()) ? "you" : "assistant");
                        row.put("message", m.getContent());
                        row.put("at", m.getCreatedAt());
                        return row;
                    }).toList());
            chats.add(chat);
        }
        return chats;
    }

    // ---------------------------------------------------------------- helpers

    /** The logged-in user's conversation; anything else is "not found" (no hint that it exists) */
    private AiConversation own(String conversationId) {
        Long userId = requireUserId();
        return conversationRepository.findByPublicIdAndUserId(conversationId == null ? "" : conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private Long currentUserId() {
        return currentUserService.current().map(AuthenticatedUser::id).orElse(null);
    }

    private Long requireUserId() {
        Long id = currentUserId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        return id;
    }

    private static String title(String text) {
        String clean = text == null ? "" : text.replaceAll("[*#_`>\\s]+", " ").trim();
        if (clean.isEmpty()) {
            return "New chat";
        }
        return clean.length() <= TITLE_LENGTH ? clean : clean.substring(0, TITLE_LENGTH - 1).trim() + "…";
    }

    private static AiMessage message(AiConversation conversation, String role, String content, String cardJson,
            String emergencyJson, LocalDateTime at) {
        AiMessage message = new AiMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setCardJson(cardJson);
        message.setEmergencyJson(emergencyJson);
        message.setCreatedAt(at);
        return message;
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
