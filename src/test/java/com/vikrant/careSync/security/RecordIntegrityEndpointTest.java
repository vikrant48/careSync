package com.vikrant.careSync.security;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signed medical records are immutable on every path, and a visit cannot be completed without one.
 * Doctor X (9101) treats patient A (9201).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecordIntegrityEndpointTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;

    private Doctor doctorX;
    private Patient patientA;

    @BeforeEach
    void setUp() {
        doctorX = new Doctor();
        doctorX.setUser(userRepository.save(user(9101L, "doctorX", User.Role.DOCTOR)));
        doctorX.setFirstName("Doc");
        doctorX.setLastName("X");
        doctorX = doctorRepository.save(doctorX);
        patientA = new Patient();
        patientA.setUser(userRepository.save(user(9201L, "patientA", User.Role.PATIENT)));
        patientA.setFirstName("Pat");
        patientA.setLastName("A");
        patientA = patientRepository.save(patientA);
    }

    private User user(Long id, String username, User.Role role) {
        return User.builder().id(id).username(username).password("x").email(username + "@test.local")
                .role(role).build();
    }

    private Long appointment(Appointment.Status status) {
        return appointmentRepository.save(Appointment.builder().doctor(doctorX).patient(patientA)
                .appointmentDateTime(LocalDateTime.now().plusHours(1)).status(status).build()).getId();
    }

    private Long record(Long appointmentId, boolean signed) {
        MedicalHistory history = new MedicalHistory();
        history.setPatient(patientA);
        history.setDoctor(doctorX);
        history.setAppointmentId(appointmentId);
        history.setVisitDate(LocalDate.now());
        history.setSymptoms("cough");
        history.setDiagnosis("bronchitis");
        history.setIsSigned(signed);
        history.setIsDraft(!signed);
        return medicalHistoryRepository.save(history).getId();
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void signedRecordCannotBeEditedOrDeletedThroughPatientEndpoints() throws Exception {
        Long id = record(appointment(Appointment.Status.MEDICAL_RECORD), true);

        mvc.perform(put("/api/patients/medical-history/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":9201,\"visitDate\":\"2026-01-01\",\"symptoms\":\"changed\",\"diagnosis\":\"changed\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/patients/medical-history/" + id)).andExpect(status().isBadRequest());

        MedicalHistory unchanged = medicalHistoryRepository.findById(id).orElseThrow();
        assertEquals("bronchitis", unchanged.getDiagnosis());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void signedRecordCannotBeDeletedThroughMedicalHistoryEndpoint() throws Exception {
        Long id = record(appointment(Appointment.Status.MEDICAL_RECORD), true);
        mvc.perform(delete("/api/medical-history/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("locked")));
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void soapDraftCannotOverwriteSignedRecord() throws Exception {
        Long appointmentId = appointment(Appointment.Status.MEDICAL_RECORD);
        Long id = record(appointmentId, true);

        mvc.perform(post("/api/appointments/" + appointmentId + "/generate-soap-draft")
                .contentType(MediaType.APPLICATION_JSON).content("{\"liveTranscript\":\"x\"}"))
                .andExpect(status().isBadRequest());

        MedicalHistory unchanged = medicalHistoryRepository.findById(id).orElseThrow();
        assertEquals(Boolean.TRUE, unchanged.getIsSigned());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void legacyCompleteRequiresSignedRecord() throws Exception {
        Long unsigned = appointment(Appointment.Status.IN_PROGRESS);
        record(unsigned, false);
        mvc.perform(put("/api/appointments/doctor/" + unsigned + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("e-sign")));

        Long signed = appointment(Appointment.Status.IN_PROGRESS);
        record(signed, true);
        mvc.perform(put("/api/appointments/doctor/" + signed + "/complete")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void closedVisitCannotBeReopenedThroughWorkflowActions() throws Exception {
        Long completed = appointment(Appointment.Status.COMPLETED);
        mvc.perform(post("/api/appointments/" + completed + "/actions/SUBMIT_INTAKE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("can no longer be changed")));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientCannotMoveVisitIntoMedicalRecordStage() throws Exception {
        Long inProgress = appointment(Appointment.Status.IN_PROGRESS);
        mvc.perform(post("/api/appointments/" + inProgress + "/actions/ADD_MEDICAL_RECORD"))
                .andExpect(status().isForbidden());
    }
}
