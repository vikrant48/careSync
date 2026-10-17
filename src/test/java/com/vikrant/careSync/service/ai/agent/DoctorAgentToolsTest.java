package com.vikrant.careSync.service.ai.agent;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.entity.Vital;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.VitalRepository;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.AuditService;
import com.vikrant.careSync.service.ai.AiClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Doctor co-pilot tools: own patients only, every read audited, dated facts, no contact details. */
class DoctorAgentToolsTest {

    private static final long DOCTOR_ID = 501L;
    private static final long OWN_PATIENT = 701L;
    private static final long OTHER_PATIENT = 702L;

    private CurrentUserService currentUser;
    private AccessGuard accessGuard;
    private PatientRepository patients;
    private AppointmentRepository appointments;
    private VitalRepository vitals;
    private AuditService audit;
    private DoctorAgentTools tools;

    private final Patient patient = Patient.builder().id(OWN_PATIENT).firstName("Test").lastName("Patient")
            .dateOfBirth(LocalDate.of(1980, 1, 15)).gender("Female").bloodGroup("O+")
            .contactInfo("+91-00000-00000, 1 Test Street").build();

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserService.class);
        accessGuard = mock(AccessGuard.class);
        patients = mock(PatientRepository.class);
        appointments = mock(AppointmentRepository.class);
        vitals = mock(VitalRepository.class);
        audit = mock(AuditService.class);
        MedicalHistoryRepository history = mock(MedicalHistoryRepository.class);
        when(history.findFinalisedByPatientId(anyLong())).thenReturn(List.of());

        when(currentUser.current()).thenReturn(Optional.of(
                new AuthenticatedUser(DOCTOR_ID, "dr.test", User.Role.DOCTOR)));
        when(accessGuard.canAccessPatient(OWN_PATIENT)).thenReturn(true);
        when(accessGuard.canAccessPatient(OTHER_PATIENT)).thenReturn(false);
        when(patients.findById(OWN_PATIENT)).thenReturn(Optional.of(patient));
        when(patients.findById(OTHER_PATIENT)).thenReturn(Optional.of(Patient.builder().id(OTHER_PATIENT)
                .firstName("Someone").lastName("Else").build()));

        tools = new DoctorAgentTools(currentUser, accessGuard, patients, appointments,
                mock(AppointmentIntakeRepository.class), vitals, history, mock(DocumentRepository.class),
                mock(BookingRepository.class), audit, new AiClock("Asia/Kolkata"));
    }

    @Test
    void definesOnlyReadOnlyTools() {
        List<String> names = tools.definitions().stream()
                .map(d -> (String) ((Map<?, ?>) d.get("function")).get("name")).toList();
        assertEquals(List.of("list_my_patients", "get_patient_summary", "get_recent_vitals", "get_medical_history",
                "list_lab_reports", "get_schedule", "brief_next_patient"), names);
    }

    @Test
    void anotherDoctorsPatientIsRefusedAndNothingIsRead() {
        AiToolResult result = tools.execute("get_recent_vitals", "{\"patient_id\":" + OTHER_PATIENT + "}");

        assertTrue(result.forModel().contains("not one of your patients"));
        assertFalse(result.forModel().contains("Someone"));
        verifyNoInteractions(vitals);
        verifyNoInteractions(audit);
    }

    @Test
    void unknownPatientGetsTheSameAnswerAsSomeoneElses() {
        when(accessGuard.canAccessPatient(999L)).thenReturn(false);
        String unknown = tools.execute("get_patient_summary", "{\"patient_id\":999}").forModel();
        String other = tools.execute("get_patient_summary", "{\"patient_id\":" + OTHER_PATIENT + "}").forModel();
        assertEquals(unknown.replace("999", "X"), other.replace(String.valueOf(OTHER_PATIENT), "X"));
    }

    @Test
    void vitalsAreDatedAndTheReadIsAudited() {
        when(vitals.findByPatientIdOrderByRecordedAtDesc(OWN_PATIENT)).thenReturn(List.of(
                Vital.builder().systolicBP(150.0).diastolicBP(95.0).heartRate(88.0)
                        .recordedAt(LocalDateTime.of(2026, 10, 12, 9, 30)).build()));

        String result = tools.execute("get_recent_vitals", "{\"patient_id\":" + OWN_PATIENT + "}").forModel();

        assertTrue(result.contains("\"bp_mmHg\":\"150/95\""), result);
        assertTrue(result.contains("12 Oct 2026"), result);
        verify(audit).log(eq("dr.test"), eq("AI_COPILOT_READ"), eq("Patient"), eq(String.valueOf(OWN_PATIENT)),
                contains("get_recent_vitals"), isNull());
    }

    @Test
    void summaryNeverContainsContactDetailsOrBirthDate() {
        String result = tools.execute("get_patient_summary", "{\"patient_id\":" + OWN_PATIENT + "}").forModel();

        assertTrue(result.contains("\"name\":\"Test Patient\""), result);
        assertTrue(result.contains("\"age\""), result);
        assertFalse(result.contains("00000"), "no phone number");
        assertFalse(result.contains("Test Street"), "no address");
        assertFalse(result.contains("1980"), "age instead of date of birth");
    }

    @Test
    void scheduleListsOnlyThisDoctorsDay() {
        LocalDate day = LocalDate.now(new AiClock("Asia/Kolkata").zone());
        when(appointments.findDoctorScheduleWithPatients(eq(DOCTOR_ID), eq(day.atStartOfDay()),
                eq(day.plusDays(1).atStartOfDay()))).thenReturn(List.of(Appointment.builder().id(9001L)
                        .patient(patient).appointmentDateTime(day.atTime(10, 0)).reason("Follow-up")
                        .status(Appointment.Status.CONFIRMED).build()));

        String result = tools.execute("get_schedule", "{}").forModel();

        assertTrue(result.contains("\"time\":\"10:00\""), result);
        assertTrue(result.contains("Follow-up"), result);
        assertFalse(result.contains("00000"), "no contact details");
    }

    @Test
    void scheduleDateFarAwayIsRefused() {
        String result = tools.execute("get_schedule", "{\"date\":\"2001-01-01\"}").forModel();
        assertTrue(result.contains("error"), result);
    }

    @Test
    void patientsCannotUseTheDoctorTools() {
        when(currentUser.current()).thenReturn(Optional.of(
                new AuthenticatedUser(OWN_PATIENT, "patient.test", User.Role.PATIENT)));

        String result = tools.execute("get_recent_vitals", "{\"patient_id\":" + OWN_PATIENT + "}").forModel();

        assertTrue(result.contains("Only doctors"), result);
        verifyNoInteractions(vitals);
    }
}
