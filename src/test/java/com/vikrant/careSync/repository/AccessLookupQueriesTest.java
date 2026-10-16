package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.projection.AppointmentParticipants;
import com.vikrant.careSync.repository.projection.DocumentOwners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the AccessGuard lookup queries against the H2 test database. */
@DataJpaTest
// Encrypted columns need the field cipher, which a JPA slice does not scan
@org.springframework.context.annotation.Import(com.vikrant.careSync.security.crypto.FieldCipher.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AccessLookupQueriesTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired
    private DocumentRepository documentRepository;

    private Doctor doctor;
    private Patient patient;
    private Patient otherPatient;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        doctor = em.persist(Doctor.builder().user(user(101L, "doctorX", User.Role.DOCTOR))
                .firstName("Doc").lastName("X").build());
        patient = em.persist(Patient.builder().user(user(201L, "patientA", User.Role.PATIENT))
                .firstName("Pat").lastName("A").build());
        otherPatient = em.persist(Patient.builder().user(user(202L, "patientB", User.Role.PATIENT))
                .firstName("Pat").lastName("B").build());
        appointment = em.persist(Appointment.builder().doctor(doctor).patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusDays(1)).status(Appointment.Status.BOOKED).build());
        em.flush();
    }

    private User user(Long id, String username, User.Role role) {
        return em.persist(User.builder().id(id).username(username).password("x")
                .email(username + "@test.local").role(role).build());
    }

    @Test
    void appointmentParticipantsAndTreatingRelationship() {
        assertEquals(Optional.of(new AppointmentParticipants(101L, 201L)),
                appointmentRepository.findParticipantsById(appointment.getId()));
        assertTrue(appointmentRepository.findParticipantsById(-1L).isEmpty());

        assertTrue(appointmentRepository.existsByDoctorIdAndPatientId(101L, 201L));
        assertFalse(appointmentRepository.existsByDoctorIdAndPatientId(101L, 202L));
    }

    @Test
    void cancelledOrRejectedAppointmentsDoNotMakeADoctorTreatThePatient() {
        for (Appointment.Status status : new Appointment.Status[] { Appointment.Status.CANCELLED,
                Appointment.Status.CANCELLED_BY_PATIENT, Appointment.Status.CANCELLED_BY_DOCTOR,
                Appointment.Status.REJECTED }) {
            em.persist(Appointment.builder().doctor(doctor).patient(otherPatient)
                    .appointmentDateTime(LocalDateTime.now().plusDays(2)).status(status).build());
        }
        em.flush();

        assertFalse(appointmentRepository.existsByDoctorIdAndPatientId(101L, 202L));
        assertEquals(List.of(201L), appointmentRepository.findTreatedPatientIds(101L));
    }

    @Test
    void medicalHistoryPatientId() {
        MedicalHistory history = new MedicalHistory();
        history.setPatient(otherPatient);
        history.setVisitDate(LocalDate.now());
        em.persistAndFlush(history);

        assertEquals(Optional.of(202L), medicalHistoryRepository.findPatientIdById(history.getId()));
        assertTrue(medicalHistoryRepository.findPatientIdById(-1L).isEmpty());
    }

    @Test
    void documentOwnersForPatientAndDoctorDocuments() {
        Document patientDoc = em.persist(document().patient(patient).build());
        Document doctorDoc = em.persist(document().doctor(doctor).documentType(Document.DocumentType.CERTIFICATE).build());
        em.flush();

        assertEquals(Optional.of(new DocumentOwners(201L, null)), documentRepository.findOwnersById(patientDoc.getId()));
        assertEquals(Optional.of(new DocumentOwners(null, 101L)), documentRepository.findOwnersById(doctorDoc.getId()));
        assertTrue(documentRepository.findOwnersById(-1L).isEmpty());
    }

    private Document.DocumentBuilder document() {
        return Document.builder().originalFilename("a.pdf").storedFilename("k/a.pdf").filePath("http://x/a.pdf")
                .fileSize(10L).contentType("application/pdf").documentType(Document.DocumentType.LAB_REPORT);
    }
}
