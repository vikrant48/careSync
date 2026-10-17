package com.vikrant.careSync.security;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.PatientSavedDoctorRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.service.SavedDoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saved doctors belong to the logged-in patient only; saving is repeatable and unknown doctors are refused. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SavedDoctorEndpointTest {

    private static final String URL = "/api/patients/me/saved-doctors";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientSavedDoctorRepository savedDoctorRepository;
    @Autowired
    private SavedDoctorService savedDoctorService;

    private Doctor doctorOne;
    private Doctor doctorTwo;

    @BeforeEach
    void setUp() {
        patient(9201L, "patientA");
        patient(9202L, "patientB");
        doctorOne = doctor(9101L, "doctorOne", "Meera");
        doctorTwo = doctor(9102L, "doctorTwo", "Arjun");
    }

    private void patient(Long id, String username) {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(User.builder().id(id).username(username).password("x")
                .email(username + "@test.local").role(User.Role.PATIENT).build()));
        patient.setFirstName("Pat");
        patient.setLastName(username);
        patientRepository.save(patient);
    }

    private Doctor doctor(Long id, String username, String firstName) {
        Doctor doctor = new Doctor();
        doctor.setUser(userRepository.save(User.builder().id(id).username(username).password("x")
                .email(username + "@test.local").role(User.Role.DOCTOR).build()));
        doctor.setFirstName(firstName);
        doctor.setLastName("Test");
        doctor.setSpecialization("Cardiology");
        return doctorRepository.save(doctor);
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientSavesListsAndRemovesDoctors() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        mvc.perform(put(URL + "/" + doctorTwo.getId())).andExpect(status().isNoContent());
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.containsInAnyOrder("doctorOne", "doctorTwo")));

        mvc.perform(delete(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        mvc.perform(get(URL))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("doctorTwo"));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void savingTwiceKeepsOneRowAndRemovingTwiceIsFine() throws Exception {
        mvc.perform(put(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        mvc.perform(put(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        assertEquals(1, savedDoctorRepository.countByPatientId(9201L));

        mvc.perform(delete(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        mvc.perform(delete(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        assertEquals(0, savedDoctorRepository.countByPatientId(9201L));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientSeesOnlyTheirOwnList() throws Exception {
        savedDoctorService.save(9202L, doctorOne.getId());

        mvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(0));
        // Removing "their" doctor from my list does not touch patient B's list
        mvc.perform(delete(URL + "/" + doctorOne.getId())).andExpect(status().isNoContent());
        assertTrue(savedDoctorRepository.existsByPatientIdAndDoctorId(9202L, doctorOne.getId()));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void unknownDoctorIsRefused() throws Exception {
        mvc.perform(put(URL + "/987654")).andExpect(status().isNotFound());
        assertEquals(0, savedDoctorRepository.countByPatientId(9201L));
    }

    @Test
    @WithMockUser(username = "doctorOne", roles = "DOCTOR")
    void onlyPatientsHaveSavedDoctors() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(put(URL + "/" + doctorTwo.getId())).andExpect(status().isForbidden());
    }
}
