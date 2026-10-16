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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Clinical records cannot be deleted through appointments, and patients can download their own data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientRecordsSafetyTest {

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

    private Doctor doctor;
    private Patient patientA;
    private Patient patientB;

    @BeforeEach
    void setUp() {
        doctor = new Doctor();
        doctor.setUser(userRepository.save(user(9101L, "doctorX", User.Role.DOCTOR)));
        doctor.setFirstName("Doc");
        doctor.setLastName("X");
        doctor = doctorRepository.save(doctor);
        patientA = patient(9201L, "patientA");
        patientB = patient(9202L, "patientB");
    }

    private User user(Long id, String username, User.Role role) {
        return User.builder().id(id).username(username).password("x").email(username + "@test.local").role(role).build();
    }

    private Patient patient(Long id, String username) {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(user(id, username, User.Role.PATIENT)));
        patient.setFirstName("Pat");
        patient.setLastName(username);
        return patientRepository.save(patient);
    }

    private Long appointment(Patient patient, String reason) {
        return appointmentRepository.save(Appointment.builder().doctor(doctor).patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusDays(1)).status(Appointment.Status.BOOKED)
                .reason(reason).build()).getId();
    }

    private void record(Patient patient, Long appointmentId, String diagnosis, boolean signed) {
        MedicalHistory history = new MedicalHistory();
        history.setPatient(patient);
        history.setDoctor(doctor);
        history.setAppointmentId(appointmentId);
        history.setVisitDate(LocalDate.now());
        history.setDiagnosis(diagnosis);
        history.setIsSigned(signed);
        history.setIsDraft(!signed);
        medicalHistoryRepository.save(history);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotDeleteAVisitWithClinicalRecords() throws Exception {
        Long withRecord = appointment(patientA, "Follow-up");
        record(patientA, withRecord, "Hypertension", true);
        mvc.perform(delete("/api/appointments/admin/" + withRecord))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Cancel it instead")));
        assertTrue(appointmentRepository.existsById(withRecord));

        Long duplicate = appointment(patientA, "Duplicate booking");
        mvc.perform(delete("/api/appointments/admin/" + duplicate)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientExportContainsOnlyTheirOwnFinalisedData() throws Exception {
        Long mine = appointment(patientA, "Persistent cough");
        record(patientA, mine, "Bronchitis", true);
        record(patientA, null, "Unreviewed AI draft", false);
        Long theirs = appointment(patientB, "Someone else's visit");
        record(patientB, theirs, "Not mine", true);

        mvc.perform(get("/api/patients/me/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.profile.lastName").value("patientA"))
                .andExpect(jsonPath("$.appointments[*].reason", contains("Persistent cough")))
                .andExpect(jsonPath("$.medicalRecords[*].diagnosis", contains("Bronchitis")))
                .andExpect(content().string(not(containsString("Not mine"))))
                .andExpect(content().string(not(containsString("Unreviewed AI draft"))));
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void onlyPatientsCanExport() throws Exception {
        mvc.perform(get("/api/patients/me/export")).andExpect(status().isForbidden());
    }
}
