package com.vikrant.careSync.security;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.PatientPaymentPreferenceRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import com.vikrant.careSync.service.PaymentPreferenceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The saved payment method is the patient's own, stored encrypted, and the demo webhook is off. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentPreferenceEndpointTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PaymentPreferenceService preferenceService;
    @Autowired
    private PatientPaymentPreferenceRepository preferenceRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Patient patientA;

    @BeforeEach
    void setUp() {
        patientA = patient(9201L, "patientA");
        patient(9202L, "patientB");
    }

    private Patient patient(Long id, String username) {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(User.builder().id(id).username(username).password("x")
                .email(username + "@test.local").role(User.Role.PATIENT).build()));
        patient.setFirstName("Pat");
        patient.setLastName(username);
        return patientRepository.save(patient);
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientSeesOnlyTheirOwnSavedMethod() throws Exception {
        mvc.perform(get("/api/payments/preferences/me")).andExpect(status().isNoContent());

        preferenceService.remember(patientA.getId(), new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.UPI, "pat.a@okbank", null, null, null));
        preferenceService.remember(9202L, new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.CARD, null, "4242", "VISA", "Pat B"));

        mvc.perform(get("/api/payments/preferences/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.upiId").value("pat.a@okbank"))
                .andExpect(jsonPath("$.cardLastFour").doesNotExist());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void latestMethodReplacesThePreviousDetails() throws Exception {
        preferenceService.remember(patientA.getId(), new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.UPI, "pat.a@okbank", null, null, null));
        preferenceService.remember(patientA.getId(), new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.CARD, null, "1111", "VISA", "Pat A"));

        assertEquals(1, preferenceRepository.count());
        mvc.perform(get("/api/payments/preferences/me"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.cardLastFour").value("1111"))
                .andExpect(jsonPath("$.cardBrand").value("VISA"))
                .andExpect(jsonPath("$.upiId").doesNotExist());
    }

    @Test
    void upiIdAndCardholderNameAreEncryptedAtRest() {
        preferenceService.remember(patientA.getId(), new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.CARD, null, "1111", "VISA", "Pat A"));
        preferenceService.remember(9202L, new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.UPI, "pat.b@okbank", null, null, null));
        entityManager.flush();

        String storedName = jdbcTemplate.queryForObject(
                "SELECT cardholder_name FROM patient_payment_preferences WHERE patient_id = 9201", String.class);
        String storedUpi = jdbcTemplate.queryForObject(
                "SELECT upi_id FROM patient_payment_preferences WHERE patient_id = 9202", String.class);
        assertNotEquals("Pat A", storedName);
        assertNotEquals("pat.b@okbank", storedUpi);
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void patientCanForgetTheSavedMethod() throws Exception {
        preferenceService.remember(patientA.getId(), new PaymentPreferenceService.SavedDetails(
                Payment.PaymentMethod.QR_CODE, null, null, null, null));

        mvc.perform(delete("/api/payments/preferences/me")).andExpect(status().isNoContent());
        mvc.perform(get("/api/payments/preferences/me")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "doctorX", roles = "DOCTOR")
    void doctorsHaveNoSavedPaymentMethod() throws Exception {
        mvc.perform(get("/api/payments/preferences/me")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/payments/preferences/me")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void webhookIsDisabledInDemoMode() throws Exception {
        mvc.perform(post("/api/payments/webhook/razorpay").header("X-Razorpay-Signature", "anything")
                .contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "patientA", roles = "PATIENT")
    void unlinkedPaymentIsRejected() throws Exception {
        mvc.perform(post("/api/payments/initiate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"paymentType\":\"OTHER\",\"paymentMethod\":\"QR_CODE\",\"patientId\":9201}"))
                .andExpect(status().isBadRequest());
    }
}
