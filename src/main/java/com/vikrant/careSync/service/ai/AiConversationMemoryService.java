package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_PREFIX = "AI:CONVERSATION:";
    private static final Duration CONVERSATION_TTL = Duration.ofHours(24);
    private static final int MAX_HISTORY_TURNS = 6; // last 6 turns (3 user + 3 assistant)

    // In-memory fallback if Redis is unavailable
    private final Map<String, List<Map<String, String>>> fallbackMemory = new ConcurrentHashMap<>();

    public List<Map<String, String>> getHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }

        String key = KEY_PREFIX + conversationId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                });
            }
        } catch (Exception e) {
            log.warn("Redis GET failed for conversation [{}], utilizing in-memory fallback: {}", conversationId,
                    e.getMessage());
            return fallbackMemory.getOrDefault(conversationId, new ArrayList<>());
        }

        return new ArrayList<>();
    }

    public void saveTurn(String conversationId, String userMsg, String assistantReply) {
        if (conversationId == null || conversationId.isBlank())
            return;

        List<Map<String, String>> history = getHistory(conversationId);

        if (userMsg != null && !userMsg.isBlank()) {
            history.add(Map.of("role", "user", "content", userMsg));
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            history.add(Map.of("role", "assistant", "content", assistantReply));
        }

        // Retain only the most recent N turns
        if (history.size() > MAX_HISTORY_TURNS) {
            history = history.subList(history.size() - MAX_HISTORY_TURNS, history.size());
        }

        String key = KEY_PREFIX + conversationId;
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, CONVERSATION_TTL);
        } catch (Exception e) {
            log.warn("Redis SET failed for conversation [{}], storing in fallback memory: {}", conversationId,
                    e.getMessage());
            fallbackMemory.put(conversationId, history);
        }
    }

    public List<Map<String, String>> getRecentHistory(String conversationId) {
        List<Map<String, String>> history = getHistory(conversationId);
        if (history.size() > MAX_HISTORY_TURNS) {
            return new ArrayList<>(history.subList(history.size() - MAX_HISTORY_TURNS, history.size()));
        }
        return history;
    }
}
