package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Responses only carry what the viewer is meant to see. */
class ResponseExposureTest {

    private Doctor doctorX;
    private Doctor doctorY;
    private Patient patientA;

    @BeforeEach
    void setUp() {
        doctorX = Doctor.builder().id(101L).firstName("Doc").lastName("X").build();
        doctorY = Doctor.builder().id(102L).firstName("Doc").lastName("Y").build();
        patientA = Patient.builder().id(201L).firstName("Pat").lastName("A").build();
        patientA.setIllnessDetails("asthma");
        patientA.setMedicalHistories(new ArrayList<>(List.of(
                record(1L, doctorX, false, true),   // signed by X
                record(2L, doctorY, false, true),   // signed by Y
                record(3L, doctorY, true, false)))); // Y's unsigned draft
    }

    private MedicalHistory record(Long id, Doctor author, boolean draft, boolean signed) {
        MedicalHistory history = new MedicalHistory();
        history.setId(id);
        history.setDoctor(author);
        history.setPatient(patientA);
        history.setVisitDate(LocalDate.now());
        history.setIsDraft(draft);
        history.setIsSigned(signed);
        return history;
    }

    private AppointmentResponse response(Appointment.Status status) {
        return new AppointmentResponse(Appointment.builder().id(500L).doctor(doctorX).patient(patientA)
                .appointmentDateTime(LocalDateTime.now()).status(status).build());
    }

    @Test
    void treatingDoctorSeesSignedHistoryButNotOtherDoctorsDrafts() {
        AppointmentResponse row = response(Appointment.Status.CONFIRMED);
        assertEquals(List.of(1L, 2L), row.getMedicalHistory().stream().map(h -> h.getId()).toList());
        assertEquals("asthma", row.getPatientIllnessDetails());
    }

    @Test
    void rejectedOrCancelledVisitCarriesNoHistory() {
        for (Appointment.Status status : List.of(Appointment.Status.REJECTED, Appointment.Status.CANCELLED_BY_PATIENT)) {
            AppointmentResponse row = response(status);
            assertTrue(row.getMedicalHistory().isEmpty(), status.name());
            assertNull(row.getPatientIllnessDetails(), status.name());
        }
    }

    @Test
    void anonymousFeedbackHidesTheAuthorFromOthers() {
        Feedback feedback = new Feedback();
        feedback.setPatient(patientA);
        feedback.setDoctor(doctorX);
        feedback.setRating(5);
        feedback.setAnonymous(true);

        FeedbackDto seenByOther = new FeedbackDto(feedback).forViewer(999L);
        assertEquals("Anonymous", seenByOther.getPatientName());
        assertNull(seenByOther.getPatientId());

        FeedbackDto seenByAuthor = new FeedbackDto(feedback).forViewer(201L);
        assertEquals(201L, seenByAuthor.getPatientId());

        feedback.setAnonymous(false);
        assertEquals(201L, new FeedbackDto(feedback).forViewer(null).getPatientId());
    }

    @Test
    void publicDoctorViewNeverReturnsTheCachedInstance() {
        DoctorDto cached = DoctorDto.builder().username("doctorX").email("x@test.local")
                .dateOfBirth(LocalDate.now().minusYears(50)).build();
        DoctorDto view = cached.forViewer(false, false);
        assertNull(view.getEmail());
        assertEquals(50, view.getAge());
        assertEquals("x@test.local", cached.getEmail(), "the cached profile is unchanged");
    }
}
