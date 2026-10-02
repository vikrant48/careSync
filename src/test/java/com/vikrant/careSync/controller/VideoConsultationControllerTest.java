package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.LiveKitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VideoConsultationControllerTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private LiveKitService liveKitService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private VideoConsultationController controller;

    private Appointment appointment;
    private Doctor doctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);

        User docUser = User.builder().id(1L).username("dr_smith").build();
        doctor = Doctor.builder().id(10L).firstName("Dr. Smith").lastName("").user(docUser).build();

        User patUser = User.builder().id(2L).username("john_doe").build();
        patient = Patient.builder().id(20L).firstName("John").lastName("Doe").user(patUser).build();

        appointment = Appointment.builder()
                .id(100L)
                .doctor(doctor)
                .patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusMinutes(5))
                .videoRoomId("appt-100-test-uuid")
                .status(Appointment.Status.SCHEDULED)
                .build();
    }

    @Test
    void getVideoToken_Success_ForDoctor() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("dr_smith");
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUsername("dr_smith")).thenReturn(Optional.of(doctor));
        when(liveKitService.generateToken(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("mocked-jwt-token");
        when(liveKitService.getLivekitUrl()).thenReturn("wss://livekit.example.com");

        ResponseEntity<?> response = controller.getVideoToken(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("mocked-jwt-token", body.get("token"));
        assertEquals("DOCTOR", body.get("role"));
    }

    @Test
    void getVideoToken_Forbidden_ForUnauthorizedUser() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("random_hacker");
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUsername("random_hacker")).thenReturn(Optional.empty());
        when(patientRepository.findByUsername("random_hacker")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getVideoToken(100L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void getVideoToken_Forbidden_OutsideTimeWindow() {
        // Appointment is 3 hours in the future (outside 10 min window)
        appointment.setAppointmentDateTime(LocalDateTime.now().plusHours(3));

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("dr_smith");
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByUsername("dr_smith")).thenReturn(Optional.of(doctor));

        ResponseEntity<?> response = controller.getVideoToken(100L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
