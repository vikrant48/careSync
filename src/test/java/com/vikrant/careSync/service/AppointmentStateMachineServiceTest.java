package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.RecordReadinessDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.AppointmentStatusLogRepository;
import com.vikrant.careSync.service.ai.AiClinicalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Workflow transition table: roles per action, allowed source statuses, closed visits stay closed. */
class AppointmentStateMachineServiceTest {

    private static final User PATIENT = User.builder().id(201L).username("patientA").role(User.Role.PATIENT).build();
    private static final User DOCTOR = User.builder().id(101L).username("doctorX").role(User.Role.DOCTOR).build();

    private AppointmentRepository appointmentRepository;
    private RecordReadinessService recordReadinessService;
    private AppointmentStateMachineService service;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        recordReadinessService = mock(RecordReadinessService.class);
        AppointmentIntakeRepository intakeRepository = mock(AppointmentIntakeRepository.class);
        when(intakeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new AppointmentStateMachineService(recordReadinessService, appointmentRepository,
                mock(AppointmentStatusLogRepository.class), mock(ApplicationEventPublisher.class),
                intakeRepository, mock(AiClinicalService.class));

        Doctor doctor = Doctor.builder().id(101L).user(DOCTOR).firstName("Doc").lastName("X").build();
        Patient patient = Patient.builder().id(201L).user(PATIENT).firstName("Pat").lastName("A").build();
        appointment = Appointment.builder().id(500L).doctor(doctor).patient(patient)
                .appointmentDateTime(LocalDateTime.now().plusHours(1)).status(Appointment.Status.BOOKED).build();
        when(appointmentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Appointment run(String action, User user) {
        return service.executeAction(500L, action, user, Map.of("reason", "test"));
    }

    @Test
    void happyPathFromBookingToReadyToComplete() {
        assertEquals(Appointment.Status.CONFIRMED, run("CONFIRM", PATIENT).getStatus());
        assertEquals(Appointment.Status.INTAKE, run("ACCEPT", DOCTOR).getStatus());
        assertEquals(Appointment.Status.WAITING_ROOM, run("JOIN_WAITING_ROOM", PATIENT).getStatus());
        assertEquals(Appointment.Status.IN_PROGRESS, run("START_PROGRESS", DOCTOR).getStatus());
        assertEquals(Appointment.Status.MEDICAL_RECORD, run("ADD_MEDICAL_RECORD", DOCTOR).getStatus());
        when(recordReadinessService.checkReadiness(500L))
                .thenReturn(RecordReadinessDto.builder().isReady(true).reasons(List.of()).build());
        assertEquals(Appointment.Status.READY_TO_COMPLETE, run("MOVE_TO_COMPLETE", DOCTOR).getStatus());
        assertEquals("doctorX", appointment.getStatusChangedBy());
    }

    @Test
    void patientCannotRunDoctorActions() {
        appointment.setStatus(Appointment.Status.IN_PROGRESS);
        assertThrows(SecurityException.class, () -> run("ADD_MEDICAL_RECORD", PATIENT));
        assertThrows(SecurityException.class, () -> run("AI_SOAP", PATIENT));
        appointment.setStatus(Appointment.Status.WAITING_ROOM);
        assertThrows(SecurityException.class, () -> run("START_PROGRESS", PATIENT));
        assertThrows(SecurityException.class, () -> run("EDIT_INTAKE", PATIENT));
    }

    @Test
    void doctorCannotRunPatientActions() {
        assertThrows(SecurityException.class, () -> run("CONFIRM", DOCTOR));
        appointment.setStatus(Appointment.Status.INTAKE);
        assertThrows(SecurityException.class, () -> run("SUBMIT_INTAKE", DOCTOR));
        assertThrows(SecurityException.class, () -> run("CONFIRM_INTAKE", DOCTOR));
    }

    @ParameterizedTest
    @EnumSource(value = Appointment.Status.class, names = { "COMPLETED", "CANCELLED_BY_PATIENT", "REJECTED", "NO_SHOW_PATIENT" })
    void closedVisitsCannotBeReopened(Appointment.Status closed) {
        appointment.setStatus(closed);
        assertThrows(IllegalStateException.class, () -> run("SUBMIT_INTAKE", PATIENT));
        assertThrows(IllegalStateException.class, () -> run("ADD_MEDICAL_RECORD", DOCTOR));
        assertThrows(IllegalStateException.class, () -> run("CANCEL", DOCTOR));
        assertEquals(closed, appointment.getStatus());
    }

    @Test
    void viewActionsStillWorkOnClosedVisits() {
        appointment.setStatus(Appointment.Status.COMPLETED);
        assertEquals(Appointment.Status.COMPLETED, run("PREVIEW_PDF", DOCTOR).getStatus());
        assertEquals(Appointment.Status.COMPLETED, run("CHAT", PATIENT).getStatus());
    }

    @Test
    void visitCannotMoveBackwards() {
        appointment.setStatus(Appointment.Status.MEDICAL_RECORD);
        assertThrows(IllegalStateException.class, () -> run("START_PROGRESS", DOCTOR));
        assertThrows(IllegalStateException.class, () -> run("JOIN_WAITING_ROOM", PATIENT));
        assertEquals(Appointment.Status.MEDICAL_RECORD, appointment.getStatus());
    }

    @Test
    void cannotSkipAhead() {
        // Straight from booking into the medical record stage is not allowed
        assertThrows(IllegalStateException.class, () -> run("ADD_MEDICAL_RECORD", DOCTOR));
        assertThrows(IllegalStateException.class, () -> run("START_PROGRESS", DOCTOR));
        assertThrows(IllegalStateException.class, () -> run("MOVE_TO_COMPLETE", DOCTOR));
    }

    @Test
    void doctorCannotCancelOnceConsultationStarted() {
        appointment.setStatus(Appointment.Status.IN_PROGRESS);
        assertThrows(IllegalStateException.class, () -> run("CANCEL", DOCTOR));
        appointment.setStatus(Appointment.Status.WAITING_ROOM);
        assertEquals(Appointment.Status.CANCELLED_BY_DOCTOR, run("CANCEL", DOCTOR).getStatus());
        assertFalse(appointment.getIsActive());
    }

    @Test
    void rejectOnlyBeforeAcceptanceAndDeactivates() {
        assertEquals(Appointment.Status.REJECTED, run("REJECT", DOCTOR).getStatus());
        assertFalse(appointment.getIsActive());

        appointment.setStatus(Appointment.Status.INTAKE);
        appointment.setIsActive(true);
        assertThrows(IllegalStateException.class, () -> run("REJECT", DOCTOR));
    }

    @Test
    void doctorJoiningRoomDoesNotChangePatientState() {
        appointment.setStatus(Appointment.Status.IN_PROGRESS);
        Appointment result = run("JOIN_VIDEO", DOCTOR);
        assertEquals(Appointment.Status.IN_PROGRESS, result.getStatus());
        assertNotEquals(Boolean.TRUE, result.getPatientReady());
    }
}
