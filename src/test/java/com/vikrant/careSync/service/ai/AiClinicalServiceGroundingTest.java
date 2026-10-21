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
import com.vikrant.careSync.repository.VitalRepository;
import com.vikrant.careSync.entity.Vital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The AI layer must never put invented vitals, diagnoses or medicines into a clinical record. */
class AiClinicalServiceGroundingTest {

    private GroqClient groqClient;
    private VitalRepository vitalRepository;
    private AiClinicalService service;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        groqClient = mock(GroqClient.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        when(geminiClient.isConfigured()).thenReturn(false);
        AppointmentIntakeRepository intakeRepository = mock(AppointmentIntakeRepository.class);
        vitalRepository = mock(VitalRepository.class);
        AiResponseCache cache = mock(AiResponseCache.class);
        service = new AiClinicalService(groqClient, geminiClient, mock(AppointmentRepository.class),
                mock(MedicalHistoryRepository.class), intakeRepository, mock(AiAuditService.class),
                cache, vitalRepository);

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
        when(groqClient.callGroq(anyString(), anyString(), eq(true), any())).thenReturn(
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
        when(groqClient.callGroq(anyString(), anyString(), eq(true), any())).thenReturn("{}");

        service.generateSoapDraft(appointment, "");

        verify(groqClient).callGroq(argThat(prompt -> prompt.contains("Do NOT invent vital signs")
                && !prompt.contains("MUST provide meaningful, realistic clinical values")), anyString(), eq(true),
                eq(AiModelTier.SMART));
    }

    @Test
    void objectiveUsesTheVisitsRecordedVitalsNeverInventedOnes() {
        when(groqClient.isConfigured()).thenReturn(false);
        when(vitalRepository.findByPatientIdOrderByRecordedAtDesc(201L)).thenReturn(List.of(Vital.builder()
                .systolicBP(150.0).diastolicBP(95.0).heartRate(88.0)
                .recordedAt(appointment.getAppointmentDateTime().minusHours(1)).build()));

        SoapReportDto draft = service.generateSoapDraft(appointment, "");

        assertTrue(draft.getObjective().startsWith("Recorded vitals ("), draft.getObjective());
        assertTrue(draft.getObjective().contains("BP 150/95 mmHg, HR 88 bpm"), draft.getObjective());
    }

    @Test
    void oldVitalsAreNotShownAsTodaysFindings() {
        when(groqClient.isConfigured()).thenReturn(false);
        when(vitalRepository.findByPatientIdOrderByRecordedAtDesc(201L)).thenReturn(List.of(Vital.builder()
                .systolicBP(150.0).diastolicBP(95.0)
                .recordedAt(appointment.getAppointmentDateTime().minusDays(30)).build()));

        assertNull(service.generateSoapDraft(appointment, "").getObjective());
    }

    @Test
    void promptAllowsOnlyMarkedSuggestionsForAssessmentAndPlan() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.callGroq(anyString(), anyString(), eq(true), any())).thenReturn("{}");

        service.generateSoapDraft(appointment, "");

        verify(groqClient).callGroq(argThat(prompt -> prompt.contains("EXCEPTION for assessment and plan only")
                && prompt.contains(AiClinicalService.SOAP_SUGGESTION_PREFIX)
                && prompt.contains("never medicines or doses")), anyString(), eq(true), eq(AiModelTier.SMART));
    }

    @Test
    void medicinesAndDosesComeBackOnePerLine() {
        when(groqClient.isConfigured()).thenReturn(true);
        when(groqClient.callGroq(anyString(), anyString(), eq(true), any())).thenReturn(
                "{\"medicine\":\"Paracetamol 500 mg; Cetirizine 10 mg\",\"doses\":\"1 tablet twice daily; 1 tablet at night\"}");

        SoapReportDto draft = service.generateSoapDraft(appointment, "doctor prescribed paracetamol and cetirizine");

        assertEquals("Paracetamol 500 mg\nCetirizine 10 mg", draft.getMedicine());
        assertEquals("1 tablet twice daily\n1 tablet at night", draft.getDoses());
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
