package com.vikrant.careSync.security.access;

import com.vikrant.careSync.entity.Notification;
import com.vikrant.careSync.entity.Patient;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Object-level access rules, usable from code or from @PreAuthorize, e.g.
 * {@code @PreAuthorize("@access.canAccessPatient(#patientId)")}.
 *
 * Rules:
 * - ADMIN may access everything.
 * - A PATIENT may access only their own records.
 * - A DOCTOR may access a patient only while treating them (at least one appointment that was not
 *   rejected or cancelled), and an appointment only if it is assigned to them.
 * Unknown ids are denied, so callers cannot probe which ids exist.
 */
@Component("access")
@RequiredArgsConstructor
public class AccessGuard {

    private final CurrentUserService currentUserService;
    private final AppointmentRepository appointmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final DocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final CertificateRepository certificateRepository;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationRepository notificationRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public boolean isAdmin() {
        return currentUserService.current().map(AuthenticatedUser::isAdmin).orElse(false);
    }

    /** True when the caller is the account with this user/doctor/patient id. */
    public boolean isSelf(Long userId) {
        return userId != null && currentUserService.current().map(u -> userId.equals(u.id())).orElse(false);
    }

    /** True when the caller is the account with this username (case-insensitive, like the profile lookups). */
    public boolean isSelfUsername(String username) {
        return username != null
                && currentUserService.current().map(u -> username.equalsIgnoreCase(u.username())).orElse(false);
    }

    public boolean canAccessPatient(Long patientId) {
        if (patientId == null) {
            return false;
        }
        Optional<AuthenticatedUser> current = currentUserService.current();
        if (current.isEmpty()) {
            return false;
        }
        AuthenticatedUser user = current.get();
        if (user.isAdmin()) {
            return true;
        }
        if (user.isPatient()) {
            return patientId.equals(user.id());
        }
        if (user.isDoctor()) {
            return appointmentRepository.existsByDoctorIdAndPatientId(user.id(), patientId);
        }
        return false;
    }

    public boolean canAccessPatientByUsername(String username) {
        if (username == null) {
            return false;
        }
        return patientRepository.findByUsername(username)
                .map(Patient::getId)
                .map(this::canAccessPatient)
                .orElse(false);
    }

    public boolean canAccessAppointment(Long appointmentId) {
        return checkAppointment(appointmentId, true, true);
    }

    /** Caller is the doctor assigned to this appointment (or ADMIN). */
    public boolean isAppointmentDoctor(Long appointmentId) {
        return checkAppointment(appointmentId, true, false);
    }

    /** Caller is the patient who booked this appointment (or ADMIN). */
    public boolean isAppointmentPatient(Long appointmentId) {
        return checkAppointment(appointmentId, false, true);
    }

    public boolean canAccessMedicalHistory(Long medicalHistoryId) {
        if (medicalHistoryId == null) {
            return false;
        }
        return medicalHistoryRepository.findPatientIdById(medicalHistoryId)
                .map(this::canAccessPatient)
                .orElse(false);
    }

    /**
     * Patient documents follow the patient rule. Documents owned only by a doctor
     * (certificates, profile images) are limited to that doctor and ADMIN.
     */
    public boolean canAccessDocument(Long documentId) {
        if (documentId == null) {
            return false;
        }
        Optional<DocumentOwners> owners = documentRepository.findOwnersById(documentId);
        if (owners.isEmpty()) {
            return false;
        }
        DocumentOwners documentOwners = owners.get();
        if (documentOwners.patientId() != null) {
            return canAccessPatient(documentOwners.patientId());
        }
        if (documentOwners.doctorId() != null) {
            return isAdmin() || isSelf(documentOwners.doctorId());
        }
        return isAdmin();
    }

    /** Doctor profile items: only the owning doctor (or ADMIN) may change them. */
    public boolean ownsCertificate(Long certificateId) {
        return certificateId != null && certificateRepository.findDoctorIdById(certificateId)
                .map(this::isSelfOrAdmin).orElse(false);
    }

    public boolean ownsEducation(Long educationId) {
        return educationId != null && educationRepository.findDoctorIdById(educationId)
                .map(this::isSelfOrAdmin).orElse(false);
    }

    public boolean ownsExperience(Long experienceId) {
        return experienceId != null && experienceRepository.findDoctorIdById(experienceId)
                .map(this::isSelfOrAdmin).orElse(false);
    }

    /** Feedback can be changed only by the patient who wrote it (or ADMIN). */
    public boolean ownsFeedback(Long feedbackId) {
        return feedbackId != null && feedbackRepository.findPatientIdById(feedbackId)
                .map(this::isSelfOrAdmin).orElse(false);
    }

    /** A notification belongs to one recipient (role + id). */
    public boolean ownsNotification(Long notificationId) {
        if (notificationId == null) {
            return false;
        }
        Optional<AuthenticatedUser> current = currentUserService.current();
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (current.isEmpty() || notification.isEmpty()) {
            return false;
        }
        AuthenticatedUser user = current.get();
        Notification n = notification.get();
        return user.id().equals(n.getRecipientId()) && user.role().name().equalsIgnoreCase(n.getRecipientType());
    }

    /** Lab-test booking: the booked patient's access rule, or the prescribing doctor. */
    public boolean canAccessBooking(Long bookingId) {
        if (bookingId == null) {
            return false;
        }
        Optional<BookingOwners> owners = bookingRepository.findOwnersById(bookingId);
        if (owners.isEmpty()) {
            return false;
        }
        BookingOwners bookingOwners = owners.get();
        return canAccessPatient(bookingOwners.patientId())
                || (bookingOwners.doctorId() != null && isSelf(bookingOwners.doctorId()));
    }

    /** Only the prescribing doctor (or ADMIN) may change a booking's status. */
    public boolean isBookingDoctor(Long bookingId) {
        if (bookingId == null) {
            return false;
        }
        return bookingRepository.findOwnersById(bookingId)
                .map(owners -> isAdmin() || (owners.doctorId() != null && isSelf(owners.doctorId())))
                .orElse(false);
    }

    public boolean canAccessPaymentTransaction(String transactionId) {
        if (transactionId == null) {
            return false;
        }
        return paymentRepository.findPatientIdByTransactionId(transactionId)
                .map(this::canAccessPatient)
                .orElse(false);
    }

    /** Patients the calling doctor currently treats; empty for non-doctors. */
    public List<Long> treatedPatientIds() {
        return currentUserService.current()
                .filter(AuthenticatedUser::isDoctor)
                .map(u -> appointmentRepository.findTreatedPatientIds(u.id()))
                .orElse(List.of());
    }

    /** For code paths that cannot use @PreAuthorize. Results in HTTP 403. */
    public void requirePatientAccess(Long patientId) {
        if (!canAccessPatient(patientId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireAppointmentAccess(Long appointmentId) {
        if (!canAccessAppointment(appointmentId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private boolean isSelfOrAdmin(Long userId) {
        return isAdmin() || isSelf(userId);
    }

    private boolean checkAppointment(Long appointmentId, boolean allowDoctor, boolean allowPatient) {
        if (appointmentId == null) {
            return false;
        }
        Optional<AuthenticatedUser> current = currentUserService.current();
        if (current.isEmpty()) {
            return false;
        }
        AuthenticatedUser user = current.get();
        Optional<AppointmentParticipants> participants = appointmentRepository.findParticipantsById(appointmentId);
        if (participants.isEmpty()) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        AppointmentParticipants p = participants.get();
        if (allowDoctor && user.isDoctor()) {
            return user.id().equals(p.doctorId());
        }
        if (allowPatient && user.isPatient()) {
            return user.id().equals(p.patientId());
        }
        return false;
    }
}
