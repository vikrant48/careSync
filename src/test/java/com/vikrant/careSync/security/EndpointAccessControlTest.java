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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end object-level access checks (IDOR) against the real controllers and security config.
 * Patient A (9201) sees doctor X (9101). Patient B (9202) is unrelated. Doctor Y (9102) has no
 * appointment with A. Doctor Z (9103) only has a cancelled appointment with A.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EndpointAccessControlTest {

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

    private Long appointmentId;
    private Long historyId;

    @BeforeEach
    void setUp() {
        userRepository.save(user(9001L, "adminUser", User.Role.ADMIN));
        Doctor doctorX = doctor(9101L, "doctorX");
        doctor(9102L, "doctorY");
        Doctor doctorZ = doctor(9103L, "doctorZ");
        Patient patientA = patient(9201L, "patientA");
        patient(9202L, "patientB");

        appointmentId = appointmentRepository.save(Appointment.builder().doctor(doctorX).patient(patientA)
                .appointmentDateTime(LocalDateTime.now().plusDays(1)).status(Appointment.Status.BOOKED).build())
                .getId();
        appointmentRepository.save(Appointment.builder().doctor(doctorZ).patient(patientA)
                .appointmentDateTime(LocalDateTime.now().plusDays(2))
                .status(Appointment.Status.CANCELLED_BY_PATIENT).build());

        MedicalHistory history = new MedicalHistory();
        history.setPatient(patientA);
        history.setDoctor(doctorX);
        history.setVisitDate(LocalDate.now());
        history.setSymptoms("cough");
        history.setDiagnosis("cold");
        history.setIsDraft(false);
        historyId = medicalHistoryRepository.save(history).getId();
    }

    private User user(Long id, String username, User.Role role) {
        return User.builder().id(id).username(username).password("x").email(username + "@test.local")
                .role(role).build();
    }

    private Doctor doctor(Long id, String username) {
        Doctor d = new Doctor();
        d.setUser(userRepository.save(user(id, username, User.Role.DOCTOR)));
        d.setFirstName("Doc");
        d.setLastName(username);
        return doctorRepository.save(d);
    }

    private Patient patient(Long id, String username) {
        Patient p = new Patient();
        p.setUser(userRepository.save(user(id, username, User.Role.PATIENT)));
        p.setFirstName("Pat");
        p.setLastName(username);
        return patientRepository.save(p);
    }

    // --- Patient records ---

    @Test
    @WithMockUser(username = "patientB", roles = "PATIENT")
    void patientCannotReadAnotherPatientsProfile() throws Exception {
        mvc.perform(get("/api/patients/9201"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientCanReadOwnProfile() throws Exception {
        mvc.perform(get("/api/patients/9201")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void doctorCannotListAllPatients() throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patientB", roles = "PATIENT")
    void patientCannotChangeAnotherPatientsProfileOrEmail() throws Exception {
        mvc.perform(put("/api/patients/9201/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"attacker@test.local\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void ownEmailChangeRequiresVerifiedAddress() throws Exception {
        mvc.perform(put("/api/patients/9201/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unverified-new@test.local\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("verify the new email")));
    }

    // --- Medical records ---

    @Test
    @WithMockUser(username = "patientB", roles = "PATIENT")
    void patientCannotReadAnotherPatientsMedicalRecord() throws Exception {
        mvc.perform(get("/api/medical-history/" + historyId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientCanReadOwnRecordButCannotDeleteIt() throws Exception {
        mvc.perform(get("/api/medical-history/" + historyId)).andExpect(status().isOk());
        mvc.perform(delete("/api/medical-history/" + historyId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void treatingDoctorCanReadPatientHistory() throws Exception {
        mvc.perform(get("/api/patients/9201/medical-history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doctorY", roles = "DOCTOR")
    void unrelatedDoctorCannotReadPatientHistory() throws Exception {
        mvc.perform(get("/api/patients/9201/medical-history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/files/patient/9201")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doctorZ", roles = "DOCTOR")
    void doctorWithOnlyCancelledAppointmentCannotReadPatientHistory() throws Exception {
        mvc.perform(get("/api/patients/9201/medical-history")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void unknownRecordIdIsDenied() throws Exception {
        mvc.perform(get("/api/medical-history/987654321")).andExpect(status().isForbidden());
    }

    // --- Appointments, chat, payments, doctor profiles ---

    @Test
    @WithMockUser(username = "patientB", roles = "PATIENT")
    void patientCannotReadAnotherPatientsIntakeOrChat() throws Exception {
        mvc.perform(get("/api/appointments/" + appointmentId + "/intake-summary")).andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/" + appointmentId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void bookedPatientCanReadOwnIntakeAndChat() throws Exception {
        mvc.perform(get("/api/appointments/" + appointmentId + "/intake-summary")).andExpect(status().isOk());
        mvc.perform(get("/api/chat/" + appointmentId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "patientB", roles = "PATIENT")
    void patientCannotListAnotherPatientsPayments() throws Exception {
        mvc.perform(get("/api/payments/patient/9201"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "doctorY", roles = "DOCTOR")
    void doctorCannotEditAnotherDoctorsProfile() throws Exception {
        mvc.perform(put("/api/doctors/profile/doctorX").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"attacker@test.local\"}"))
                .andExpect(status().isForbidden());
    }
}
