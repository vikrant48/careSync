package com.vikrant.careSync.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Configured models in order, next model on retired / rate-limited ones, limits and reasoning options sent. */
class GroqClientTest {

    private static final String URL = "https://groq.test/chat";
    private static final String OK = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}";

    private GroqClient client;
    private AiQuotaService quota;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        quota = mock(AiQuotaService.class);
        client = new GroqClient("test-key", URL, "openai/gpt-oss-120b", "openai/gpt-oss-120b, openai/gpt-oss-20b",
                "openai/gpt-oss-20b", 512, "low", 1000, 1000, quota);
        server = MockRestServiceServer.bindTo(client.restTemplate()).build();
    }

    @Test
    void candidateModelsKeepOrderWithoutDuplicatesOrBlanks() {
        assertEquals(List.of("a", "b", "c"), GroqClient.candidateModels("a", " b, a ,, c "));
        assertEquals(List.of("a"), GroqClient.candidateModels("a", null));
    }

    @Test
    void sendsLimitsAndReasoningOptionsToTheConfiguredModel() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.max_completion_tokens").value(512))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.include_reasoning").value(false))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertEquals("hello", client.callGroq("system", "hi", true));
        assertEquals("openai/gpt-oss-120b", client.getLastUsedModel());
        server.verify();
    }

    @Test
    void movesToTheNextModelWhenRateLimitedOrRetired() {
        server.expect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));
        server.expect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertEquals("hello", client.callGroq("system", "hi", false));
        assertEquals("openai/gpt-oss-20b", client.getLastUsedModel());
        server.verify();
    }

    @Test
    void fastTasksStartOnTheSmallModelAndReportTokens() {
        server.expect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"short\"}}],"
                        + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("short", client.callGroq("system", "hi", false, AiModelTier.FAST));
        assertEquals("openai/gpt-oss-20b", client.getLastUsedModel());
        assertEquals(new AiUsage(12, 3, 15), client.getLastUsage());
        server.verify();
    }

    @Test
    void everyCallCountsAgainstTheUsersAllowance() {
        doThrow(new AiQuotaExceededException("limit")).when(quota).consume();

        assertThrows(AiQuotaExceededException.class, () -> client.callGroq("system", "hi", false));
        server.verify(); // no request reached the provider
    }

    @Test
    void otherRefusalsStopWithoutLeakingDetails() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid key\"}"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client.callGroq("s", "hi", false));
        assertEquals("AI request was refused (HTTP 401)", e.getMessage());
        server.verify();
    }

    @Test
    void reportsUnavailableWhenEveryModelFails() {
        server.expect(jsonPath("$.model").value("openai/gpt-oss-120b")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(jsonPath("$.model").value("openai/gpt-oss-20b")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(IllegalStateException.class, () -> client.callGroq("s", "hi", false));
        server.verify();
    }
}
