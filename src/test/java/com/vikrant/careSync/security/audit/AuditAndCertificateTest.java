package com.vikrant.careSync.security.audit;

import com.vikrant.careSync.entity.AuditLog;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AuditLogRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Patient-data requests leave an audit row without patient data; certificate links are https only.
 * Not @Transactional: audit rows are written on their own thread and must be visible here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditAndCertificateTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;

    @AfterEach
    void cleanUp() {
        auditService.awaitIdle(5000);
        auditLogRepository.deleteAll();
        doctorRepository.findAll().stream().filter(d -> d.getId() == 9701L).forEach(doctorRepository::delete);
        userRepository.findById(9701L).ifPresent(userRepository::delete);
    }

    private List<AuditLog> entries() {
        assertTrue(auditService.awaitIdle(5000));
        return auditLogRepository.findAll();
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void refusedPatientReadIsRecordedWithIdOnly() throws Exception {
        mvc.perform(get("/api/patients/9201")).andExpect(status().isForbidden());

        AuditLog entry = entries().stream().filter(e -> e.getEntityName().equals("GET /api/patients/{id}"))
                .findFirst().orElseThrow();
        assertEquals("doctorX", entry.getUsername());
        assertEquals("READ_DENIED", entry.getAction());
        assertEquals("id=9201", entry.getEntityId());
        assertEquals("status=403", entry.getDetails());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void textFromTheUrlNeverReachesTheAuditLog() throws Exception {
        mvc.perform(get("/api/patients/illness/diabetes"));

        AuditLog entry = entries().stream()
                .filter(e -> e.getEntityName().equals("GET /api/patients/illness/{illnessKeyword}"))
                .findFirst().orElseThrow();
        assertEquals("N/A", entry.getEntityId());
        assertFalse(entry.toString().toLowerCase().contains("diabetes"));
        assertFalse(String.valueOf(entry.getDetails()).contains("diabetes"));
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void nonPatientEndpointsAreNotAudited() throws Exception {
        mvc.perform(get("/api/doctors/public/nobody"));
        assertTrue(entries().isEmpty());
    }

    @Test
    void actionNames() {
        assertEquals("READ", AuditTrailInterceptor.action("GET", 200));
        assertEquals("UPDATE_DENIED", AuditTrailInterceptor.action("PUT", 403));
        assertEquals("DELETE_FAILED", AuditTrailInterceptor.action("DELETE", 500));
        assertEquals("CREATE", AuditTrailInterceptor.action("POST", 201));
    }

    @Test
    @WithMockUser(username = "doctorCert", roles = "DOCTOR")
    void certificateLinksMustBeHttps() throws Exception {
        Doctor doctor = new Doctor();
        // No surrounding transaction here: the doctor's cascade creates the user in the same insert
        doctor.setUser(User.builder().id(9701L).username("doctorCert").password("x")
                .email("doctorCert@test.local").role(User.Role.DOCTOR).build());
        doctor.setFirstName("Doc");
        doctor.setLastName("X");
        doctorRepository.save(doctor);

        for (String link : List.of("http://169.254.169.254/latest/meta-data/", "javascript:alert(1)",
                "file:///etc/passwd", "https://user:pass@example.com/x")) {
            mvc.perform(post("/api/doctors/profile/doctorCert/certificates").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"MBBS\",\"url\":\"" + link + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/doctors/profile/doctorCert/certificates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"MBBS\",\"url\":\"https://council.example.org/verify/123\"}"))
                .andExpect(status().isOk());
    }
}
