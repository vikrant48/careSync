package com.vikrant.careSync.security;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DoctorRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Doctor contact details are for logged-in patients; date of birth and last login stay private. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorProfileVisibilityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;

    @BeforeEach
    void setUp() {
        Doctor doctor = new Doctor();
        doctor.setUser(userRepository.save(User.builder().id(9101L).username("doctorX").password("x")
                .email("doctorX@test.local").role(User.Role.DOCTOR).build()));
        doctor.setFirstName("Doc");
        doctor.setLastName("X");
        doctor.setSpecialization("Cardiology");
        doctor.setContactInfo("9000000001");
        doctor.setAlternateContactInfo("9000000002");
        doctor.setDateOfBirth(LocalDate.now().minusYears(40).minusDays(3));
        doctorRepository.save(doctor);

        // The viewers must exist: the visibility rules read the viewer's role from the database
        userRepository.save(User.builder().id(9201L).username("patientA").password("x")
                .email("patientA@test.local").role(User.Role.PATIENT).build());
        userRepository.save(User.builder().id(9102L).username("doctorY").password("x")
                .email("doctorY@test.local").role(User.Role.DOCTOR).build());
    }

    @Test
    void anonymousVisitorSeesNoContactOrBirthDate() throws Exception {
        mvc.perform(get("/api/doctors/public/doctorX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Doc"))
                .andExpect(jsonPath("$.specialization").value("Cardiology"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.contactInfo").doesNotExist())
                .andExpect(jsonPath("$.alternateContactInfo").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.lastLogin").doesNotExist())
                .andExpect(jsonPath("$.age").value(40));
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void loggedInPatientSeesContactButNotBirthDate() throws Exception {
        mvc.perform(get("/api/doctors/public/doctorX"))
                .andExpect(jsonPath("$.email").value("doctorX@test.local"))
                .andExpect(jsonPath("$.contactInfo").value("9000000001"))
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.age").value(40));
        mvc.perform(get("/api/doctors/specialization/Cardiology"))
                .andExpect(jsonPath("$[0].contactInfo").value("9000000001"))
                .andExpect(jsonPath("$[0].dateOfBirth").doesNotExist());
    }

    @Test
    @WithMockUser(username = "doctorY", roles = "DOCTOR")
    void otherDoctorSeesNoContact() throws Exception {
        mvc.perform(get("/api/doctors/profile/doctorX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.contactInfo").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void doctorSeesTheirOwnFullProfile() throws Exception {
        mvc.perform(get("/api/doctors/profile/doctorX"))
                .andExpect(jsonPath("$.email").value("doctorX@test.local"))
                .andExpect(jsonPath("$.alternateContactInfo").value("9000000002"))
                .andExpect(jsonPath("$.dateOfBirth").exists());
    }
}
