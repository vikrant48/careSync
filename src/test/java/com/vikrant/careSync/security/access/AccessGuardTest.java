package com.vikrant.careSync.security.access;

import com.vikrant.careSync.entity.Notification;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.CertificateRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.EducationRepository;
import com.vikrant.careSync.repository.ExperienceRepository;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.NotificationRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.PaymentRepository;
import com.vikrant.careSync.repository.projection.AppointmentParticipants;
import com.vikrant.careSync.repository.projection.BookingOwners;
import com.vikrant.careSync.repository.projection.DocumentOwners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Object-level access rules (IDOR protection). Ids used below:
 * patient A = 201, patient B = 202, doctor X = 101, doctor Y = 102, admin = 1.
 */
class AccessGuardTest {

    private static final AuthenticatedUser PATIENT_A = new AuthenticatedUser(201L, "patientA", User.Role.PATIENT);
    private static final AuthenticatedUser DOCTOR_X = new AuthenticatedUser(101L, "doctorX", User.Role.DOCTOR);
    private static final AuthenticatedUser DOCTOR_Y = new AuthenticatedUser(102L, "doctorY", User.Role.DOCTOR);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", User.Role.ADMIN);

    private CurrentUserService currentUserService;
    private AppointmentRepository appointmentRepository;
    private MedicalHistoryRepository medicalHistoryRepository;
    private DocumentRepository documentRepository;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        appointmentRepository = mock(AppointmentRepository.class);
        medicalHistoryRepository = mock(MedicalHistoryRepository.class);
        documentRepository = mock(DocumentRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EducationRepository educationRepository = mock(EducationRepository.class);
        ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        guard = new AccessGuard(currentUserService, appointmentRepository, medicalHistoryRepository,
                documentRepository, patientRepository, certificateRepository, educationRepository,
                experienceRepository, feedbackRepository, notificationRepository, bookingRepository,
                paymentRepository);

        // Certificate 900 / education 901 / experience 902 belong to doctor X.
        when(certificateRepository.findDoctorIdById(anyLong())).thenAnswer(inv -> ownerOf(inv.getArgument(0), 900L, 101L));
        when(educationRepository.findDoctorIdById(anyLong())).thenAnswer(inv -> ownerOf(inv.getArgument(0), 901L, 101L));
        when(experienceRepository.findDoctorIdById(anyLong())).thenAnswer(inv -> ownerOf(inv.getArgument(0), 902L, 101L));
        // Feedback 910 was written by patient A.
        when(feedbackRepository.findPatientIdById(anyLong())).thenAnswer(inv -> ownerOf(inv.getArgument(0), 910L, 201L));
        // Booking 920: patient A, prescribed by doctor Y. Booking 921: patient A self-booked.
        when(bookingRepository.findOwnersById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 920L) return Optional.of(new BookingOwners(201L, 102L));
            if (id == 921L) return Optional.of(new BookingOwners(201L, null));
            return Optional.empty();
        });
        when(paymentRepository.findPatientIdByTransactionId("TXN_A")).thenReturn(Optional.of(201L));
        when(paymentRepository.findPatientIdByTransactionId("TXN_B")).thenReturn(Optional.of(202L));
        Notification forPatientA = new Notification();
        forPatientA.setRecipientType("PATIENT");
        forPatientA.setRecipientId(201L);
        when(notificationRepository.findById(930L)).thenReturn(Optional.of(forPatientA));
        Patient patientA = new Patient();
        patientA.setId(201L);
        when(patientRepository.findByUsername("patientA")).thenReturn(Optional.of(patientA));
        when(appointmentRepository.findTreatedPatientIds(101L)).thenReturn(List.of(201L));

        // Appointment 500: doctor X with patient A. Doctor X treats patient A only.
        when(appointmentRepository.findParticipantsById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return id == 500L ? Optional.of(new AppointmentParticipants(101L, 201L)) : Optional.empty();
        });
        when(appointmentRepository.existsByDoctorIdAndPatientId(anyLong(), anyLong())).thenAnswer(inv ->
                Long.valueOf(101L).equals(inv.getArgument(0)) && Long.valueOf(201L).equals(inv.getArgument(1)));

        // Medical history 700 belongs to patient A; 701 to patient B.
        when(medicalHistoryRepository.findPatientIdById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 700L) return Optional.of(201L);
            if (id == 701L) return Optional.of(202L);
            return Optional.empty();
        });

        // Document 800 belongs to patient A; 801 is doctor X's certificate.
        when(documentRepository.findOwnersById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 800L) return Optional.of(new DocumentOwners(201L, null));
            if (id == 801L) return Optional.of(new DocumentOwners(null, 101L));
            return Optional.empty();
        });
    }

    private static Optional<Long> ownerOf(Long id, Long knownId, Long ownerId) {
        return knownId.equals(id) ? Optional.of(ownerId) : Optional.empty();
    }

    private void loggedInAs(AuthenticatedUser user) {
        when(currentUserService.current()).thenReturn(Optional.ofNullable(user));
    }

    @Nested
    class PatientRecords {

        @Test
        void patientCanAccessOwnRecord() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessPatient(201L));
        }

        @Test
        void patientCannotAccessAnotherPatient() {
            loggedInAs(PATIENT_A);
            assertFalse(guard.canAccessPatient(202L));
        }

        @Test
        void doctorCanAccessOwnPatient() {
            loggedInAs(DOCTOR_X);
            assertTrue(guard.canAccessPatient(201L));
        }

        @Test
        void doctorCannotAccessPatientWithoutAppointment() {
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessPatient(201L));
        }

        @Test
        void adminCanAccessAnyPatient() {
            loggedInAs(ADMIN);
            assertTrue(guard.canAccessPatient(202L));
        }

        @Test
        void anonymousAndNullIdAreDenied() {
            loggedInAs(null);
            assertFalse(guard.canAccessPatient(201L));
            loggedInAs(PATIENT_A);
            assertFalse(guard.canAccessPatient(null));
        }
    }

    @Nested
    class Appointments {

        @Test
        void bookedPatientAndAssignedDoctorCanAccess() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessAppointment(500L));
            loggedInAs(DOCTOR_X);
            assertTrue(guard.canAccessAppointment(500L));
        }

        @Test
        void otherPatientAndOtherDoctorAreDenied() {
            loggedInAs(new AuthenticatedUser(202L, "patientB", User.Role.PATIENT));
            assertFalse(guard.canAccessAppointment(500L));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessAppointment(500L));
        }

        @Test
        void unknownAppointmentIsDeniedEvenForAdmin() {
            loggedInAs(ADMIN);
            assertFalse(guard.canAccessAppointment(999L));
            assertTrue(guard.canAccessAppointment(500L));
        }

        @Test
        void doctorOnlyAndPatientOnlyChecksRespectRole() {
            loggedInAs(PATIENT_A);
            assertFalse(guard.isAppointmentDoctor(500L));
            assertTrue(guard.isAppointmentPatient(500L));
            loggedInAs(DOCTOR_X);
            assertTrue(guard.isAppointmentDoctor(500L));
            assertFalse(guard.isAppointmentPatient(500L));
        }
    }

    @Nested
    class MedicalHistories {

        @Test
        void followsPatientRule() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessMedicalHistory(700L));
            assertFalse(guard.canAccessMedicalHistory(701L));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessMedicalHistory(700L));
        }

        @Test
        void unknownRecordIsDenied() {
            loggedInAs(ADMIN);
            assertFalse(guard.canAccessMedicalHistory(999L));
        }
    }

    @Nested
    class Documents {

        @Test
        void patientDocumentFollowsPatientRule() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessDocument(800L));
            loggedInAs(DOCTOR_X);
            assertTrue(guard.canAccessDocument(800L));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessDocument(800L));
        }

        @Test
        void doctorOnlyDocumentIsLimitedToOwnerAndAdmin() {
            loggedInAs(DOCTOR_X);
            assertTrue(guard.canAccessDocument(801L));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessDocument(801L));
            loggedInAs(PATIENT_A);
            assertFalse(guard.canAccessDocument(801L));
            loggedInAs(ADMIN);
            assertTrue(guard.canAccessDocument(801L));
        }

        @Test
        void unknownDocumentIsDenied() {
            loggedInAs(ADMIN);
            assertFalse(guard.canAccessDocument(999L));
        }
    }

    @Nested
    class SelfChecks {

        @Test
        void selfByIdAndUsername() {
            loggedInAs(DOCTOR_X);
            assertTrue(guard.isSelf(101L));
            assertFalse(guard.isSelf(102L));
            assertTrue(guard.isSelfUsername("DoctorX"));
            assertFalse(guard.isSelfUsername("doctorY"));
            assertFalse(guard.isAdmin());
        }
    }

    @Nested
    class OwnedItems {

        @Test
        void doctorProfileItemsBelongToTheirDoctor() {
            loggedInAs(DOCTOR_X);
            assertTrue(guard.ownsCertificate(900L));
            assertTrue(guard.ownsEducation(901L));
            assertTrue(guard.ownsExperience(902L));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.ownsCertificate(900L));
            assertFalse(guard.ownsEducation(901L));
            assertFalse(guard.ownsExperience(902L));
            assertFalse(guard.ownsCertificate(999L));
        }

        @Test
        void feedbackBelongsToItsAuthor() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.ownsFeedback(910L));
            loggedInAs(new AuthenticatedUser(202L, "patientB", User.Role.PATIENT));
            assertFalse(guard.ownsFeedback(910L));
        }

        @Test
        void notificationBelongsToRecipientRoleAndId() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.ownsNotification(930L));
            // Same numeric id with a different role must not match
            loggedInAs(new AuthenticatedUser(201L, "someDoctor", User.Role.DOCTOR));
            assertFalse(guard.ownsNotification(930L));
            loggedInAs(PATIENT_A);
            assertFalse(guard.ownsNotification(999L));
        }
    }

    @Nested
    class BookingsAndPayments {

        @Test
        void bookingVisibleToPatientTreatingDoctorAndPrescriber() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessBooking(921L));
            loggedInAs(DOCTOR_X); // treats patient A
            assertTrue(guard.canAccessBooking(921L));
            loggedInAs(DOCTOR_Y); // prescribed booking 920 but does not treat patient A
            assertTrue(guard.canAccessBooking(920L));
            assertFalse(guard.canAccessBooking(921L));
        }

        @Test
        void onlyPrescribingDoctorChangesBookingStatus() {
            loggedInAs(DOCTOR_Y);
            assertTrue(guard.isBookingDoctor(920L));
            loggedInAs(DOCTOR_X);
            assertFalse(guard.isBookingDoctor(920L));
            assertFalse(guard.isBookingDoctor(921L));
        }

        @Test
        void paymentFollowsPatientRule() {
            loggedInAs(PATIENT_A);
            assertTrue(guard.canAccessPaymentTransaction("TXN_A"));
            assertFalse(guard.canAccessPaymentTransaction("TXN_B"));
            assertFalse(guard.canAccessPaymentTransaction("UNKNOWN"));
        }
    }

    @Nested
    class UsernameAndTreatedPatients {

        @Test
        void patientByUsernameFollowsPatientRule() {
            loggedInAs(DOCTOR_X);
            assertTrue(guard.canAccessPatientByUsername("patientA"));
            loggedInAs(DOCTOR_Y);
            assertFalse(guard.canAccessPatientByUsername("patientA"));
            assertFalse(guard.canAccessPatientByUsername("nobody"));
        }

        @Test
        void treatedPatientIdsOnlyForDoctors() {
            loggedInAs(DOCTOR_X);
            assertEquals(List.of(201L), guard.treatedPatientIds());
            loggedInAs(PATIENT_A);
            assertTrue(guard.treatedPatientIds().isEmpty());
        }
    }
}
