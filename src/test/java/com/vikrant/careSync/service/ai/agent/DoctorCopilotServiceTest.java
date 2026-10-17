package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.ai.AiClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The @patient choice is checked before the AI runs; the prompt asks for cited facts. */
class DoctorCopilotServiceTest {

    private AiAgentService agent;
    private DoctorAgentTools tools;
    private AccessGuard accessGuard;
    private PatientRepository patients;
    private DoctorCopilotService copilot;

    @BeforeEach
    void setUp() {
        agent = mock(AiAgentService.class);
        tools = mock(DoctorAgentTools.class);
        accessGuard = mock(AccessGuard.class);
        patients = mock(PatientRepository.class);
        copilot = new DoctorCopilotService(agent, tools, accessGuard, mock(CurrentUserService.class), patients,
                mock(AppointmentRepository.class), new AiClock("Asia/Kolkata"));
    }

    @Test
    void anotherDoctorsPatientIsRefusedBeforeTheAiRuns() {
        when(accessGuard.canAccessPatient(702L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> copilot.answer("summarize", 702L, List.of(), null));
        verifyNoInteractions(agent);
        verify(patients, never()).findById(any());
    }

    @Test
    void pickedPatientIsNamedInThePromptAndToolsAreTheDoctorOnes() {
        when(accessGuard.canAccessPatient(701L)).thenReturn(true);
        when(patients.findById(701L)).thenReturn(Optional.of(
                Patient.builder().id(701L).firstName("Test").lastName("Patient").build()));

        copilot.answer("Any red flags?", 701L, List.of(), null);

        verify(agent).runWith(argThat(prompt -> prompt.contains("patient_id 701 (Test Patient)")
                && prompt.contains("[vitals, 12 Oct 2026]")), same(tools), eq("Any red flags?"), anyList(), isNull());
    }

    @Test
    void withoutAPickNoPatientIsNamed() {
        copilot.answer("Brief me on my next patient", null, List.of(), null);

        verify(agent).runWith(argThat(prompt -> !prompt.contains("picked this patient")), same(tools),
                anyString(), anyList(), isNull());
        verifyNoInteractions(accessGuard);
    }
}
