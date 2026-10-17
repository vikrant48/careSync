package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.AppointmentFollowThroughDto;
import com.vikrant.careSync.entity.AiVisitSummary;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AiVisitSummaryRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClinicalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** After-visit summary: written once, saved, reused; written again only when the visit record changes. */
class AppointmentVisitSummaryTest {

    private static final long APPOINTMENT_ID = 301L;
    private static final long PATIENT_ID = 801L;

    private AiClinicalService clinical;
    private AiVisitSummaryRepository summaries;
    private AppointmentFollowThroughService service;

    @BeforeEach
    void setUp() {
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        MedicalHistoryRepository histories = mock(MedicalHistoryRepository.class);
        clinical = mock(AiClinicalService.class);
        summaries = mock(AiVisitSummaryRepository.class);
        service = new AppointmentFollowThroughService(appointments, histories, mock(FeedbackRepository.class),
                mock(DoctorLeaveService.class), clinical, mock(AiBookingService.class), summaries);

        Appointment visit = Appointment.builder().id(APPOINTMENT_ID).status(Appointment.Status.COMPLETED)
                .patient(Patient.builder().id(PATIENT_ID).build())
                .doctor(Doctor.builder().id(12L).firstName("Test").lastName("Doctor").isActive(true).build())
                .build();
        when(appointments.findByIdWithDetails(APPOINTMENT_ID)).thenReturn(Optional.of(visit));
        when(histories.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(clinical.visitSummaryFacts(any(), any(), any(), any(), any(), any())).thenReturn("recorded facts");
        when(clinical.visitSummaryFallback(anyString())).thenReturn("fallback");
        when(summaries.save(any(AiVisitSummary.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void firstOpenWritesAndSavesTheSummary() {
        when(summaries.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(clinical.writeVisitSummary("recorded facts")).thenReturn("Your visit in plain words.");

        AppointmentFollowThroughDto result = service.getFollowThrough(APPOINTMENT_ID, PATIENT_ID);

        assertEquals("Your visit in plain words.", result.getPlainLanguageSummary());
        assertNotNull(result.getPlainLanguageSummarySavedAt());
        verify(summaries).save(argThat(s -> s.getAppointmentId() == APPOINTMENT_ID
                && s.getSourceHash().equals(sha256("recorded facts"))));
    }

    @Test
    void laterOpensReuseTheSavedSummaryWithoutTheAi() {
        when(summaries.findById(APPOINTMENT_ID)).thenReturn(Optional.of(saved(sha256("recorded facts"))));

        AppointmentFollowThroughDto result = service.getFollowThrough(APPOINTMENT_ID, PATIENT_ID);

        assertEquals("Saved summary", result.getPlainLanguageSummary());
        verify(clinical, never()).writeVisitSummary(any());
        verify(summaries, never()).save(any());
    }

    @Test
    void anAmendedRecordGetsANewSummary() {
        when(summaries.findById(APPOINTMENT_ID)).thenReturn(Optional.of(saved(sha256("the record before"))));
        when(clinical.writeVisitSummary("recorded facts")).thenReturn("Updated summary");

        AppointmentFollowThroughDto result = service.getFollowThrough(APPOINTMENT_ID, PATIENT_ID);

        assertEquals("Updated summary", result.getPlainLanguageSummary());
        verify(summaries).save(argThat(s -> s.getSourceHash().equals(sha256("recorded facts"))));
    }

    @Test
    void withoutTheAiTheRecordedFactsAreShownAndNothingIsSaved() {
        when(summaries.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(clinical.writeVisitSummary(any())).thenReturn(null);

        AppointmentFollowThroughDto result = service.getFollowThrough(APPOINTMENT_ID, PATIENT_ID);

        assertEquals("fallback", result.getPlainLanguageSummary());
        assertNull(result.getPlainLanguageSummarySavedAt());
        verify(summaries, never()).save(any());
    }

    private static AiVisitSummary saved(String hash) {
        AiVisitSummary summary = new AiVisitSummary();
        summary.setAppointmentId(APPOINTMENT_ID);
        summary.setSummary("Saved summary");
        summary.setSourceHash(hash);
        summary.setCreatedAt(LocalDateTime.now().minusDays(1));
        summary.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return summary;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
