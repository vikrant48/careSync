package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.security.crypto.FieldCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Short chat memory sent back to the model with each message. Conversations belong to the logged-in user (the key
 * includes the username, so another user's conversation id finds nothing) and are stored encrypted (AES-256-GCM),
 * because messages can contain health details.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final FieldCipher fieldCipher;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_PREFIX = "AI:CONVERSATION:";
    static final Duration CONVERSATION_TTL = Duration.ofHours(24);
    static final int MAX_HISTORY_MESSAGES = 10; // last 5 exchanges (user + assistant)
    private static final int MAX_FALLBACK_CONVERSATIONS = 500;

    private record FallbackEntry(String encrypted, Instant expiresAt) {
    }

    /** Used only while Redis is down: bounded (oldest dropped first) and entries expire like the Redis ones */
    private final Map<String, FallbackEntry> fallbackMemory = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FallbackEntry> eldest) {
            return size() > MAX_FALLBACK_CONVERSATIONS;
        }
    };

    public List<Map<String, String>> getRecentHistory(String conversationId) {
        List<Map<String, String>> history = getHistory(conversationId);
        if (history.size() > MAX_HISTORY_MESSAGES) {
            return new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }
        return history;
    }

    public List<Map<String, String>> getHistory(String conversationId) {
        Optional<String> key = key(conversationId);
        if (key.isEmpty()) {
            return new ArrayList<>();
        }
        String stored;
        try {
            stored = redisTemplate.opsForValue().get(key.get());
        } catch (Exception e) {
            log.warn("Redis unavailable for AI conversation memory, using in-memory fallback");
            stored = fallbackGet(key.get());
        }
        return decode(stored);
    }

    public void saveTurn(String conversationId, String userMsg, String assistantReply) {
        Optional<String> key = key(conversationId);
        if (key.isEmpty()) {
            return;
        }
        List<Map<String, String>> history = getHistory(conversationId);
        if (userMsg != null && !userMsg.isBlank()) {
            history.add(Map.of("role", "user", "content", userMsg));
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            history.add(Map.of("role", "assistant", "content", assistantReply));
        }
        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }

        String encrypted;
        try {
            encrypted = fieldCipher.encrypt(objectMapper.writeValueAsString(history));
        } catch (Exception e) {
            log.warn("Could not store AI conversation memory: {}", e.getClass().getSimpleName());
            return;
        }
        try {
            redisTemplate.opsForValue().set(key.get(), encrypted, CONVERSATION_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable for AI conversation memory, using in-memory fallback");
            synchronized (fallbackMemory) {
                fallbackMemory.put(key.get(), new FallbackEntry(encrypted, Instant.now().plus(CONVERSATION_TTL)));
            }
        }
    }

    /** No logged-in user or an odd id: no memory at all */
    private Optional<String> key(String conversationId) {
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9-]{8,64}")) {
            return Optional.empty();
        }
        return currentUserService.currentUsername()
                .filter(username -> !username.isBlank())
                .map(username -> KEY_PREFIX + username + ":" + conversationId);
    }

    private String fallbackGet(String key) {
        synchronized (fallbackMemory) {
            FallbackEntry entry = fallbackMemory.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.expiresAt().isBefore(Instant.now())) {
                fallbackMemory.remove(key);
                return null;
            }
            return entry.encrypted();
        }
    }

    /** Anything unreadable (missing, older plaintext format, wrong key) counts as an empty history */
    private List<Map<String, String>> decode(String stored) {
        if (stored == null || stored.isBlank() || !fieldCipher.isCurrent(stored)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(fieldCipher.decryptCurrent(stored),
                    new TypeReference<List<Map<String, String>>>() {
                    });
        } catch (Exception e) {
            log.warn("Discarding unreadable AI conversation memory: {}", e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }
}
