package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.security.crypto.FieldCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Same question: answered once and stored encrypted. Changed record: asked again. */
class AiResponseCacheTest {

    private final Map<String, String> redis = new HashMap<>();
    private AiResponseCache cache;
    private final AtomicInteger modelCalls = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> redis.get(inv.<String>getArgument(0)));
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        cache = new AiResponseCache(template, new FieldCipher("test-secret", true));
    }

    private String ask(String facts) {
        return cache.getOrCompute("visit_summary", facts, Duration.ofDays(1), () -> {
            modelCalls.incrementAndGet();
            return "Summary of " + facts;
        });
    }

    @Test
    void sameQuestionIsAnsweredOnceAndStoredEncrypted() {
        assertEquals("Summary of asthma visit", ask("asthma visit"));
        assertEquals("Summary of asthma visit", ask("asthma visit"));
        assertEquals(1, modelCalls.get());
        assertTrue(redis.values().stream().noneMatch(v -> v.contains("asthma")), "stored encrypted");
        assertTrue(redis.keySet().stream().noneMatch(k -> k.contains("asthma")), "key is a hash, not the text");
    }

    @Test
    void changedRecordIsAskedAgain() {
        ask("asthma visit");
        ask("asthma visit, inhaler added");
        assertEquals(2, modelCalls.get());
    }

    @Test
    void emptyAnswersAreNotKept() {
        cache.getOrCompute("visit_summary", "x", Duration.ofDays(1), () -> null);
        assertTrue(redis.isEmpty());
    }
}
