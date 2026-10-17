package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.DoctorDashboardMetricsResponse;
import com.vikrant.careSync.dto.SearchRequestDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Batch 15: the SQL rewrites return what the in-memory versions returned. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QueryRewritesTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Doctor doctor;
    private Patient patientA;
    private Patient patientB;

    @BeforeEach
    void setUp() {
        doctor = new Doctor();
        doctor.setUser(userRepository.save(User.builder().id(9101L).username("doctorX").password("x")
                .email("doctorX@test.local").role(User.Role.DOCTOR).build()));
        doctor.setFirstName("Meera");
        doctor.setLastName("100%_Kapoor");
        doctor.setSpecialization("Cardiology");
        doctor = doctorRepository.save(doctor);
        patientA = patient(9201L, "patientA");
        patientB = patient(9202L, "patientB");
    }

    private Patient patient(Long id, String username) {
        Patient patient = new Patient();
        patient.setUser(userRepository.save(User.builder().id(id).username(username).password("x")
                .email(username + "@test.local").role(User.Role.PATIENT).build()));
        patient.setFirstName("Pat");
        patient.setLastName(username);
        return patientRepository.save(patient);
    }

    private Appointment appointment(Patient patient, LocalDateTime at, Appointment.Status status) {
        return appointmentRepository.save(Appointment.builder().doctor(doctor).patient(patient)
                .appointmentDateTime(at).status(status).build());
    }

    @Test
    void doctorTextSearchWorksAndTreatsWildcardsLiterally() {
        // Used to throw: the query referenced an "email" attribute Doctor does not have
        assertEquals(1, doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder().query("meera").build()).size());
        assertEquals(1, doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder().query("100%_").build()).size());
        assertEquals(0, doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder().query("1_0").build()).size(),
                "_ is a literal character, not 'any one character'");
        assertEquals(1, doctorRepository.countDoctorsDynamic(SearchRequestDto.builder().query("kapoor").build()));
    }

    @Test
    void doctorSearchFiltersByVerification() {
        // New doctors start unverified
        assertEquals(1, doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder().query("meera").verified(false).build()).size());
        assertEquals(0, doctorRepository.countDoctorsDynamic(SearchRequestDto.builder().query("meera").verified(true).build()));

        doctor.setIsVerified(true);
        doctorRepository.saveAndFlush(doctor);
        assertEquals(1, doctorRepository.countDoctorsDynamic(SearchRequestDto.builder().query("meera").verified(true).build()));
        assertEquals(0, doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder().query("meera").verified(false).build()).size());
        assertEquals(1, doctorRepository.countDoctorsDynamic(SearchRequestDto.builder().query("meera").build()),
                "No verified filter: both kinds");
    }

    @Test
    void todaysAppointmentsUseTheDayRange() {
        LocalDate today = LocalDate.now();
        appointment(patientA, today.atTime(9, 0), Appointment.Status.BOOKED);
        appointment(patientA, today.atTime(23, 59), Appointment.Status.BOOKED);
        appointment(patientB, today.plusDays(1).atStartOfDay(), Appointment.Status.BOOKED);
        appointment(patientB, today.minusDays(1).atTime(23, 59), Appointment.Status.BOOKED);

        assertEquals(2, appointmentService.getTodayAppointments(doctor.getId()).size());
    }

    @Test
    void dashboardMetricsFromAggregates() {
        LocalDate today = LocalDate.now();
        appointment(patientA, today.atTime(10, 0), Appointment.Status.MEDICAL_RECORD);
        appointment(patientA, today.minusMonths(2).atTime(10, 0), Appointment.Status.COMPLETED);
        appointment(patientB, today.atTime(11, 0), Appointment.Status.BOOKED);
        appointment(patientB, today.minusDays(1).atTime(11, 0), Appointment.Status.REPORT_DRAFTED);
        Feedback feedback = new Feedback();
        feedback.setDoctor(doctor);
        feedback.setPatient(patientA);
        feedback.setRating(4);
        feedbackRepository.save(feedback);
        Feedback second = new Feedback();
        second.setDoctor(doctor);
        second.setPatient(patientB);
        second.setRating(5);
        feedbackRepository.save(second);
        entityManager.flush();
        // Both appointments in a pending-report status reached it before yesterday ended
        jdbc.update("UPDATE appointments SET status_changed_at = ?, created_at = ? WHERE doctor_id = ?",
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3), doctor.getId());
        entityManager.clear();

        DoctorDashboardMetricsResponse metrics = appointmentService.getDoctorDashboardMetrics(doctor.getId());

        assertEquals(2, metrics.getTodayAppointments());
        assertEquals(1, metrics.getAppointmentChangeFromYesterday());
        assertEquals(2, metrics.getTotalPatients());
        assertEquals(2, metrics.getPendingReports());
        assertEquals(0, metrics.getPendingReportChangeFromYesterday());
        assertEquals(4.5, metrics.getSatisfactionRating());
        assertEquals(2, metrics.getReviewCount());
    }

    @Test
    void paymentLookupKeepsBookingAndAppointmentIdsApart() {
        assertThrows(RuntimeException.class, () -> paymentService.getPaymentFor(999_999L, "APPOINTMENT"));
        assertThrows(RuntimeException.class, () -> paymentService.getPaymentFor(999_999L, "BOOKING"));
    }

    @Autowired
    private ReportingService reportingService;

    @Test
    void doctorAppointmentPagesAreSortedAndPagedInSql() {
        LocalDateTime now = LocalDateTime.now();
        Appointment soon = appointment(patientA, now.plusHours(2), Appointment.Status.BOOKED);
        Appointment later = appointment(patientB, now.plusDays(3), Appointment.Status.CONFIRMED);
        Appointment recent = appointment(patientA, now.minusHours(5), Appointment.Status.COMPLETED);
        Appointment older = appointment(patientB, now.minusDays(9), Appointment.Status.COMPLETED);

        // ALL: upcoming soonest first, then past most recent first (same order as the old in-memory sort)
        var all = appointmentService.getDoctorAppointmentsPaginated(doctor.getId(), 0, 10, "ALL", "ALL", null);
        assertEquals(java.util.List.of(soon.getId(), later.getId(), recent.getId(), older.getId()),
                all.stream().map(r -> r.getAppointmentId()).toList());

        var secondPage = appointmentService.getDoctorAppointmentsPaginated(doctor.getId(), 1, 2, "ALL", "ALL", null);
        assertEquals(java.util.List.of(recent.getId(), older.getId()),
                secondPage.stream().map(r -> r.getAppointmentId()).toList());

        assertEquals(2, appointmentService.countDoctorAppointments(doctor.getId(), "ALL", "UPCOMING", null));
        assertEquals(2, appointmentService.countDoctorAppointments(doctor.getId(), "COMPLETED", "PAST", null));
        assertEquals(1, appointmentService.countDoctorAppointments(doctor.getId(), "PENDING", "ALL", null));
        assertEquals(0, appointmentService.countDoctorAppointments(doctor.getId(), "NOT_A_STATUS", "ALL", null));
    }

    @Test
    void firstOpenSlotSkipsBookedTimes() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        var first = appointmentService.findFirstAvailableSlot(doctor.getId(), 14).orElseThrow();
        // Book that exact slot: the next search must move on
        LocalDateTime taken = first.date().atTime(java.time.LocalTime.parse(first.time()));
        appointment(patientA, taken, Appointment.Status.BOOKED);
        entityManager.flush();
        var next = appointmentService.findFirstAvailableSlot(doctor.getId(), 14).orElseThrow();
        assertNotEquals(first, next);
        assertFalse(next.date().isAfter(tomorrow.plusDays(1)), "a free slot is found within a day or two");
    }

    @Test
    void clinicOverviewCountsFromAggregates() {
        LocalDate today = LocalDate.now();
        appointment(patientA, today.atTime(9, 0), Appointment.Status.COMPLETED);
        appointment(patientB, today.atTime(10, 0), Appointment.Status.CANCELLED);
        appointment(patientB, today.minusDays(40).atTime(10, 0), Appointment.Status.COMPLETED);

        var report = reportingService.getClinicOverviewReport(today.minusDays(1), today);
        assertEquals(2L, report.get("totalAppointments"));
        assertEquals(1L, report.get("totalCompletedAppointments"));
        assertEquals(1L, report.get("totalCancelledAppointments"));

        var trends = reportingService.getAppointmentTrendsReport(today.minusDays(1), today);
        assertEquals(java.util.Map.of(today.toString(), 2L), trends.get("dailyTrends"));
    }
}
