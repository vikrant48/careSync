package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.service.ai.AiAuditService;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiChatHistoryService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import com.vikrant.careSync.service.ai.AiConversationMemoryService;
import com.vikrant.careSync.service.ai.GeminiClient;
import com.vikrant.careSync.service.ai.GroqClient;
import com.vikrant.careSync.service.ai.agent.AiAgentService;
import com.vikrant.careSync.service.ai.agent.DoctorCopilotService;
import com.vikrant.careSync.service.ai.triage.EmergencyTriageService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Chat messages over 2000 characters are refused before any AI call (they would only use up tokens). */
class AiChatMessageLimitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestValidationRejectsOversizedMessages() {
        AiChatRequest atLimit = AiChatRequest.builder().message("a".repeat(AiChatRequest.MAX_MESSAGE_LENGTH)).build();
        AiChatRequest tooLong = AiChatRequest.builder().message("a".repeat(AiChatRequest.MAX_MESSAGE_LENGTH + 1)).build();

        assertTrue(validator.validate(atLimit).isEmpty());
        assertEquals(1, validator.validate(tooLong).size());
    }

    @Test
    void serviceRefusesOversizedMessagesEvenWithoutValidation() {
        GroqClient groq = mock(GroqClient.class);
        AiAgentService agent = mock(AiAgentService.class);
        DoctorCopilotService copilot = mock(DoctorCopilotService.class);
        AiChatHistoryService history = mock(AiChatHistoryService.class);
        AiService service = new AiService(groq, mock(AiBookingService.class), mock(AiClinicalService.class),
                mock(AiConversationMemoryService.class), mock(DoctorRepository.class), mock(PatientRepository.class),
                mock(AiAuditService.class), mock(GeminiClient.class), mock(AccessGuard.class), agent,
                mock(EmergencyTriageService.class), history, mock(SupabaseStorageService.class), copilot);

        AiChatResponse response = service.getResponse(AiChatRequest.builder()
                .message("a".repeat(AiChatRequest.MAX_MESSAGE_LENGTH + 1)).build());

        assertFalse(response.isSuccess());
        assertTrue(response.getResponse().contains("too long"));
        verifyNoInteractions(agent, copilot, history);
        verify(groq, never()).callGroq(anyString(), anyString(), anyBoolean());
    }
}
