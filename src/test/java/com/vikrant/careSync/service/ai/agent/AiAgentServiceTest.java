package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiModelTier;
import com.vikrant.careSync.service.ai.AiToolCall;
import com.vikrant.careSync.service.ai.AiToolTurn;
import com.vikrant.careSync.service.ai.AiUsage;
import com.vikrant.careSync.service.ai.GroqClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The agent runs requested tools, sends results back, returns the final text with the last card. */
class AiAgentServiceTest {

    private GroqClient groq;
    private PatientAgentTools tools;
    private AiAgentService agent;

    private final AiBookingSuggestion doctorsCard = AiBookingSuggestion.builder()
            .type(AiBookingSuggestion.SuggestionType.DOCTORS)
            .doctors(List.of(AiBookingSuggestion.DoctorSuggestion.builder().id(12L).name("Asha Rao")
                    .recommendedDate("2026-10-20").recommendedSlot("10:00").build()))
            .build();

    @BeforeEach
    void setUp() {
        groq = mock(GroqClient.class);
        tools = mock(PatientAgentTools.class);
        AiBookingService booking = mock(AiBookingService.class);
        when(booking.getAvailableSpecializations()).thenReturn(List.of("Cardiology", "Dermatology"));
        when(tools.definitions()).thenReturn(List.of(Map.of("type", "function")));
        agent = new AiAgentService(groq, tools, booking);
    }

    private static AiToolTurn toolTurn(String name, String args) {
        return new AiToolTurn(null, List.of(new AiToolCall("call_1", name, args)), "openai/gpt-oss-120b",
                new AiUsage(100, 10, 110));
    }

    private static AiToolTurn textTurn(String text) {
        return new AiToolTurn(text, List.of(), "openai/gpt-oss-120b", new AiUsage(150, 30, 180));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runsToolsThenAnswersWithTheCard() {
        when(groq.callWithTools(anyList(), anyList(), eq(AiModelTier.SMART)))
                .thenReturn(toolTurn("search_doctors", "{\"specialization\":\"Cardiology\"}"))
                .thenReturn(textTurn("Dr. Asha Rao has a slot tomorrow at 10:00."));
        when(tools.execute("search_doctors", "{\"specialization\":\"Cardiology\"}"))
                .thenReturn(new AiToolResult("{\"doctors\":[{\"doctor_id\":12}]}", doctorsCard));

        AiAgentService.AgentReply reply = agent.run("I need a heart doctor", List.of());

        assertEquals("Dr. Asha Rao has a slot tomorrow at 10:00.", reply.text());
        assertSame(doctorsCard, reply.card());
        assertEquals(List.of("search_doctors"), reply.toolsUsed());
        assertEquals(new AiUsage(250, 40, 290), reply.usage(), "tokens of both steps are added up");

        // Second call carries the assistant's tool call and the tool result back to the model
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(groq, times(2)).callWithTools(messages.capture(), anyList(), any());
        List<Map<String, Object>> second = messages.getAllValues().get(1);
        assertEquals("system", second.get(0).get("role"));
        assertTrue(((String) second.get(0).get("content")).contains("Cardiology, Dermatology"));
        assertEquals("tool", second.get(second.size() - 1).get("role"));
        assertEquals("call_1", second.get(second.size() - 1).get("tool_call_id"));
    }

    @Test
    void stopsAfterTheStepLimitAndOffersNoToolsOnTheLastStep() {
        when(groq.callWithTools(anyList(), anyList(), any()))
                .thenReturn(toolTurn("search_doctors", "{}"));
        when(tools.execute(anyString(), anyString())).thenReturn(new AiToolResult("{}", doctorsCard));

        AiAgentService.AgentReply reply = agent.run("loop forever", List.of());

        verify(groq, times(AiAgentService.MAX_STEPS)).callWithTools(anyList(), anyList(), any());
        verify(groq, times(1)).callWithTools(anyList(), eq(List.of()), any());
        assertEquals("Here's what I found.", reply.text());
        assertSame(doctorsCard, reply.card());
    }

    @Test
    void refusesTooManyToolCallsInOneStep() {
        List<AiToolCall> many = List.of(
                new AiToolCall("a", "search_doctors", "{}"), new AiToolCall("b", "search_doctors", "{}"),
                new AiToolCall("c", "search_doctors", "{}"), new AiToolCall("d", "search_doctors", "{}"));
        when(groq.callWithTools(anyList(), anyList(), any()))
                .thenReturn(new AiToolTurn(null, many, "m", AiUsage.NONE))
                .thenReturn(textTurn("done"));
        when(tools.execute(anyString(), anyString())).thenReturn(AiToolResult.data("{}"));

        agent.run("many", List.of());

        verify(tools, times(AiAgentService.MAX_TOOL_CALLS_PER_STEP)).execute(anyString(), anyString());
    }

    @Test
    void cardNoteLetsFollowUpsReferToWhatWasShown() {
        String note = AiAgentService.cardNote(doctorsCard);
        assertTrue(note.contains("Asha Rao (doctor_id 12, first open 2026-10-20 10:00)"));
        assertEquals("", AiAgentService.cardNote(null));
    }
}
