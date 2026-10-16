package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.dto.SoapReportDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The AI layer must never put invented vitals, diagnoses or medicines into a clinical record. */
class AiClinicalServiceGroundingTest {

    private GroqClient groqClient;
    private AiClinicalService service;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        groqClient = mock(GroqClient.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        when(geminiClient.isConfigured()).thenReturn(false);
        AppointmentIntakeRepository intakeRepository = mock(AppointmentIntakeRepository.class);
        service = new AiClinicalService(groqClient, geminiClient, mock(AppointmentRepository.class),
                mock(MedicalHistoryRepository.class), intakeRepository);

        appointment = Appointment.builder().id(500L)
                .patient(Patient.builder().id(201L).firstName("Pat").lastName("A").build())
                .doctor(Doctor.builder().id(101L).firstName("Doc").lastName("X").build())
                .appointmentDateTime(LocalDateTime.now()).build();
        when(intakeRepository.findByAppointmentId(500L)).thenReturn(Optional.of(AppointmentIntake.builder()
                .chiefComplaint("persistent cough").symptoms("cough, mild fever").symptomDuration("4-7 Days").build()));
    }

    @Test
    void withoutAiClinicalFieldsStayEmpty() {
        when(groqClient.isConfigured()).thenReturn(false);

        SoapReportDto draft = service.generateSoapDraft(appointment, "");

        assertNull(draft.getObjective(), "no invented vitals");
        assertNull(draft.getDiagnosis(), "no invented diagnosis");
        assertNull(draft.getAssessment());
        assertNull(draft.getPlan());
        assertNull(draft.getMedicine(), "no invented prescription");
        assertNull(draft.getDoses());
        assertNull(draft.getTreatment());
        // Patient-reported facts may be prefilled
        assertEquals("Patient reports: persistent cough. Duration: 4-7 Days.", draft.getSubjective());
        assertEquals("cough, mild fever", draft.getSymptoms());
        assertTrue(draft.getNotes().contains("AI draft unavailable"));
    }

    @Test
    void placeholderAnswersFromTheModelAreTreatedAsNotDocumented() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.callGroq(anyString(), anyString(), eq(true))).thenReturn(
                "{\"subjective\":\"Cough for 5 days\",\"objective\":\"Not recorded\",\"assessment\":\"\","
                        + "\"plan\":\"Review in 1 week\",\"diagnosis\":\"N/A\",\"medicine\":\"\",\"doses\":\"\"}");

        SoapReportDto draft = service.generateSoapDraft(appointment, "doctor transcript");

        assertEquals("Cough for 5 days", draft.getSubjective());
        assertEquals("Review in 1 week", draft.getPlan());
        assertNull(draft.getObjective());
        assertNull(draft.getDiagnosis());
        assertNull(draft.getMedicine());
        assertTrue(draft.getNotes().contains("Verify every section before signing"));
    }

    @Test
    void promptForbidsInventedFindings() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.callGroq(anyString(), anyString(), eq(true))).thenReturn("{}");

        service.generateSoapDraft(appointment, "");

        verify(groqClient).callGroq(argThat(prompt -> prompt.contains("Do NOT invent vital signs")
                && !prompt.contains("MUST provide meaningful, realistic clinical values")), anyString(), eq(true));
    }

    @Test
    void diagnosisSuggestionsHaveNoHardCodedMedicinesWhenAiIsUnavailable() {
        when(groqClient.isConfigured()).thenReturn(false);

        DiagnosisSuggestionDto result = service.suggestDiagnosis("fever and headache");

        assertNotNull(result.getSuggestions());
        assertTrue(result.getSuggestions().isEmpty());
        assertTrue(result.getDisclaimer().contains("unavailable"));
    }
}
