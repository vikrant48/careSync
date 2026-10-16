package com.vikrant.careSync.security.crypto;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The job rewrites old ECB and plaintext values into GCM, and the app still reads every row afterwards. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FieldReencryptionServiceTest {

    @Autowired
    private FieldReencryptionService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    @SuppressWarnings("unchecked")
    void rewritesLegacyValuesAndKeepsThemReadable() throws Exception {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(User.builder().id(9201L).username("patientA").password("x")
                .email("patientA@test.local").role(User.Role.PATIENT).build()));
        patient.setFirstName("Asha");
        patient.setLastName("Verma");
        patient = patientRepository.save(patient);
        Doctor doctor = new Doctor();
        doctor.setUser(userRepository.save(User.builder().id(9101L).username("doctorX").password("x")
                .email("doctorX@test.local").role(User.Role.DOCTOR).build()));
        doctor.setFirstName("Doc");
        doctor.setLastName("X");
        doctor = doctorRepository.save(doctor);
        Long appointmentId = appointmentRepository.save(Appointment.builder().doctor(doctor).patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusDays(1)).status(Appointment.Status.BOOKED)
                .reason("placeholder").build()).getId();
        entityManager.flush();

        // Put the rows back into their pre-release state
        jdbcTemplate.update("UPDATE patients SET first_name = ?, last_name = ? WHERE user_id = 9201",
                FieldEncryptionTest.legacyEcb("Asha"), "Verma (seeded plaintext)");
        jdbcTemplate.update("UPDATE appointments SET reason = ? WHERE id = ?", "Fever for 3 days", appointmentId);
        entityManager.clear();

        Map<String, Object> dryRun = service.reencryptAll(true);
        assertEquals(3L, dryRun.get("valuesToRewrite"));
        assertEquals("Fever for 3 days",
                jdbcTemplate.queryForObject("SELECT reason FROM appointments WHERE id = ?", String.class, appointmentId),
                "a dry run changes nothing");

        Map<String, Object> run = service.reencryptAll(false);
        assertEquals(3L, run.get("valuesRewritten"));
        assertEquals(0L, run.get("failed"));

        String storedFirst = jdbcTemplate.queryForObject("SELECT first_name FROM patients WHERE user_id = 9201", String.class);
        String storedReason = jdbcTemplate.queryForObject("SELECT reason FROM appointments WHERE id = ?", String.class,
                appointmentId);
        assertTrue(storedFirst.startsWith("v2:"));
        assertTrue(storedReason.startsWith("v2:"));
        assertFalse(storedReason.contains("Fever"));

        entityManager.clear();
        Patient reloaded = patientRepository.findById(9201L).orElseThrow();
        assertEquals("Asha", reloaded.getFirstName());
        assertEquals("Verma (seeded plaintext)", reloaded.getLastName());
        assertEquals("Fever for 3 days", appointmentRepository.findById(appointmentId).orElseThrow().getReason());

        Map<String, Object> again = service.reencryptAll(true);
        assertEquals(0L, again.get("valuesToRewrite"), "nothing left; safe to switch on strict mode");
        Map<String, Object> reasonColumn = (Map<String, Object>) ((Map<String, Object>) again.get("columns"))
                .get("appointments.reason");
        assertEquals(1L, reasonColumn.get("alreadyCurrent"));
    }
}
