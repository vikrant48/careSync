package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.security.crypto.FieldCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chat memory is stored encrypted, belongs to one user, and keeps only the last messages. */
class AiConversationMemoryServiceTest {

    private static final String CONVERSATION = "3f2b9c1e-1111-2222-3333-444455556666";

    private final Map<String, String> redis = new HashMap<>();
    private CurrentUserService currentUser;
    private AiConversationMemoryService memory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> redis.get(inv.<String>getArgument(0)));
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        currentUser = mock(CurrentUserService.class);
        when(currentUser.currentUsername()).thenReturn(Optional.of("patientA"));
        memory = new AiConversationMemoryService(template, new FieldCipher("test-secret", true), currentUser);
    }

    @Test
    void storedEncryptedAndReadBack() {
        memory.saveTurn(CONVERSATION, "I have a headache", "Rest and drink water.");

        String stored = redis.get("AI:CONVERSATION:patientA:" + CONVERSATION);
        assertNotNull(stored);
        assertFalse(stored.contains("headache"), "health details are not stored in plain text");
        assertEquals(List.of(
                Map.of("role", "user", "content", "I have a headache"),
                Map.of("role", "assistant", "content", "Rest and drink water.")), memory.getRecentHistory(CONVERSATION));
    }

    @Test
    void anotherUserWithTheSameConversationIdSeesNothing() {
        memory.saveTurn(CONVERSATION, "my symptoms", "advice");

        when(currentUser.currentUsername()).thenReturn(Optional.of("patientB"));
        assertTrue(memory.getRecentHistory(CONVERSATION).isEmpty());
    }

    @Test
    void noUserOrMalformedIdMeansNoMemory() {
        memory.saveTurn("bad id!", "x", "y");
        assertTrue(redis.isEmpty());

        when(currentUser.currentUsername()).thenReturn(Optional.empty());
        memory.saveTurn(CONVERSATION, "x", "y");
        assertTrue(redis.isEmpty());
    }

    @Test
    void keepsOnlyTheLatestMessages() {
        for (int i = 1; i <= 8; i++) {
            memory.saveTurn(CONVERSATION, "question " + i, "answer " + i);
        }
        List<Map<String, String>> history = memory.getRecentHistory(CONVERSATION);
        assertEquals(AiConversationMemoryService.MAX_HISTORY_MESSAGES, history.size());
        assertEquals("answer 8", history.get(history.size() - 1).get("content"));
        assertEquals("question 4", history.get(0).get("content"));
    }

    @Test
    void oldPlaintextHistoryIsIgnored() {
        redis.put("AI:CONVERSATION:patientA:" + CONVERSATION, "[{\"role\":\"user\",\"content\":\"old\"}]");
        assertTrue(memory.getRecentHistory(CONVERSATION).isEmpty());
    }
}
