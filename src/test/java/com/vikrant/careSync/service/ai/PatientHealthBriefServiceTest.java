package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.dto.PatientHealthBriefDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The health brief: facts from the records, AI tips from facts without names, checklist without the AI. */
class PatientHealthBriefServiceTest {

    private static final long PATIENT_ID = 801L;

    private AppointmentRepository appointments;
    private BookingRepository bookings;
    private DocumentRepository documents;
    private AiClinicalService clinical;
    private PatientHealthBriefService service;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.current()).thenReturn(Optional.of(
                new AuthenticatedUser(PATIENT_ID, "patient.test", User.Role.PATIENT)));
        appointments = mock(AppointmentRepository.class);
        bookings = mock(BookingRepository.class);
        documents = mock(DocumentRepository.class);
        clinical = mock(AiClinicalService.class);
        service = new PatientHealthBriefService(currentUser, appointments, bookings, documents, clinical,
                new AiClock("Asia/Kolkata"));

        Doctor doctor = Doctor.builder().id(12L).firstName("Test").lastName("Doctor")
                .specialization("Cardiology").build();
        Appointment next = Appointment.builder().id(301L).doctor(doctor).reason("Chest tightness when climbing stairs")
                .appointmentDateTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .status(Appointment.Status.BOOKED).intakeCompleted(false).build();
        when(appointments.findPatientComingAppointments(eq(PATIENT_ID), any())).thenReturn(List.of(next));
        Booking lab = Booking.builder().id(41L).status(Booking.BookingStatus.PENDING)
                .bookingDate(LocalDateTime.now().minusDays(1))
                .selectedTests(List.of(LabTest.builder().testName("Lipid Profile").build())).build();
        when(bookings.findPendingWithTestsByPatientId(PATIENT_ID)).thenReturn(List.of(lab));
        when(documents.findUnreadLabReports(PATIENT_ID)).thenReturn(List.of(Document.builder().id(55L)
                .originalFilename("cbc.pdf").uploadDate(LocalDateTime.now()).build()));
    }

    @Test
    void factsComeFromTheRecordsAndTipsFromTheAi() {
        when(clinical.preparationTips(anyString())).thenReturn("- Bring your reports\n- Note when the tightness starts\nIgnored line");

        PatientHealthBriefDto brief = service.brief();

        assertEquals(301L, brief.getNextAppointment().getAppointmentId());
        assertFalse(brief.getNextAppointment().isPreVisitFormDone());
        assertEquals(List.of("Bring your reports", "Note when the tightness starts"), brief.getPreparationTips());
        assertTrue(brief.isTipsFromAi());
        assertEquals(List.of("PRE_VISIT_FORM", "LAB_RESULTS_PENDING"),
                brief.getPendingFollowUps().stream().map(PatientHealthBriefDto.PendingItem::getType).toList());
        assertTrue(brief.getPendingFollowUps().get(1).getText().contains("Lipid Profile"));
        assertEquals(55L, brief.getUnreadLabReports().get(0).getDocumentId());
    }

    @Test
    void theAiGetsNoNamesOrIds() {
        when(clinical.preparationTips(anyString())).thenReturn(null);
        service.brief();

        ArgumentCaptor<String> facts = ArgumentCaptor.forClass(String.class);
        verify(clinical).preparationTips(facts.capture());
        assertTrue(facts.getValue().contains("Cardiology"));
        assertTrue(facts.getValue().contains("Lipid Profile"));
        assertFalse(facts.getValue().contains("Test Doctor"), "no doctor name");
        assertFalse(facts.getValue().contains("301"), "no appointment id");
    }

    @Test
    void withoutTheAiTheStandardChecklistIsShown() {
        when(clinical.preparationTips(anyString())).thenReturn(null);

        PatientHealthBriefDto brief = service.brief();

        assertFalse(brief.isTipsFromAi());
        assertTrue(brief.getPreparationTips().get(0).contains("pre-visit form"));
    }

    @Test
    void nothingBookedMeansNoAiCall() {
        when(appointments.findPatientComingAppointments(eq(PATIENT_ID), any())).thenReturn(List.of());

        PatientHealthBriefDto brief = service.brief();

        assertNull(brief.getNextAppointment());
        assertTrue(brief.getPreparationTips().isEmpty());
        verifyNoInteractions(clinical);
    }

    @Test
    void tipsAreParsedFromBulletLinesOnly() {
        assertEquals(List.of("One", "Two", "Three"),
                PatientHealthBriefService.parseTips("Intro\n- One\n* **Two**\n• Three\n- Four"));
        assertEquals(List.of(), PatientHealthBriefService.parseTips(null));
    }
}
