package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.dto.PatientHealthBriefDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The patient dashboard's "AI health brief". The facts (next appointment, pending items, unread lab reports) come
 * straight from the records, with no AI. The AI only adds 2-3 preparation tips, written from facts without names
 * or contact details and cached until those facts change; without the AI a standard checklist is shown.
 */
@Service
@RequiredArgsConstructor
public class PatientHealthBriefService {

    static final int MAX_ITEMS = 5;
    static final String NOTE = "Tips are general guidance from CareSync AI, not medical advice.";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final CurrentUserService currentUserService;
    private final AppointmentRepository appointmentRepository;
    private final BookingRepository bookingRepository;
    private final DocumentRepository documentRepository;
    private final AiClinicalService clinicalService;
    private final AiClock clock;

    public PatientHealthBriefDto brief() {
        Long patientId = currentUserService.current()
                .filter(AuthenticatedUser::isPatient)
                .map(AuthenticatedUser::id)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        LocalDateTime now = LocalDateTime.now(clock.zone());

        List<Appointment> coming = appointmentRepository.findPatientComingAppointments(patientId, now);
        List<Booking> pendingLabs = bookingRepository.findPendingWithTestsByPatientId(patientId);
        List<PatientHealthBriefDto.LabReportItem> unread = documentRepository.findUnreadLabReports(patientId).stream()
                .limit(MAX_ITEMS)
                .map(d -> PatientHealthBriefDto.LabReportItem.builder()
                        .documentId(d.getId())
                        .filename(d.getOriginalFilename())
                        .uploadedAt(d.getUploadDate())
                        .explained(d.getAiExplanation() != null)
                        .build())
                .toList();

        List<PatientHealthBriefDto.PendingItem> pending = new ArrayList<>();
        coming.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIntakeCompleted()))
                .limit(MAX_ITEMS)
                .forEach(a -> pending.add(PatientHealthBriefDto.PendingItem.builder()
                        .type("PRE_VISIT_FORM")
                        .text("Fill in the pre-visit form for your visit with " + doctorName(a) + " on "
                                + a.getAppointmentDateTime().format(DAY) + ".")
                        .appointmentId(a.getId())
                        .date(a.getAppointmentDateTime())
                        .build()));
        pendingLabs.stream()
                .limit(MAX_ITEMS)
                .forEach(b -> pending.add(PatientHealthBriefDto.PendingItem.builder()
                        .type("LAB_RESULTS_PENDING")
                        .text("Results pending for " + testNames(b) + " (booked "
                                + (b.getBookingDate() != null ? b.getBookingDate().format(DAY) : "earlier") + ").")
                        .bookingId(b.getId())
                        .date(b.getBookingDate())
                        .build()));

        PatientHealthBriefDto.NextAppointment next = null;
        List<String> tips = List.of();
        boolean tipsFromAi = false;
        if (!coming.isEmpty()) {
            Appointment a = coming.get(0);
            next = PatientHealthBriefDto.NextAppointment.builder()
                    .appointmentId(a.getId())
                    .doctorName(doctorName(a))
                    .specialization(a.getDoctor() != null ? a.getDoctor().getSpecialization() : null)
                    .dateTime(a.getAppointmentDateTime())
                    .status(a.getStatus() != null ? a.getStatus().name() : null)
                    .preVisitFormDone(Boolean.TRUE.equals(a.getIntakeCompleted()))
                    .build();
            List<String> aiTips = parseTips(clinicalService.preparationTips(facts(a, now, pendingLabs, unread.size())));
            tipsFromAi = !aiTips.isEmpty();
            tips = tipsFromAi ? aiTips : standardTips(a);
        }

        return PatientHealthBriefDto.builder()
                .nextAppointment(next)
                .preparationTips(tips)
                .tipsFromAi(tipsFromAi)
                .pendingFollowUps(pending)
                .unreadLabReports(unread)
                .note(NOTE)
                .build();
    }

    /** What the AI is told: no names, contact details or ids */
    static String facts(Appointment next, LocalDateTime now, List<Booking> pendingLabs, int unreadReports) {
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), next.getAppointmentDateTime().toLocalDate());
        StringBuilder facts = new StringBuilder("Next appointment: ")
                .append(next.getDoctor() != null && next.getDoctor().getSpecialization() != null
                        ? next.getDoctor().getSpecialization() : "doctor")
                .append(" visit ").append(days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days")
                .append(" (").append(next.getAppointmentDateTime().format(WHEN)).append(").\n");
        String reason = patientReason(next.getReason());
        if (reason != null) {
            facts.append("Reason the patient gave: ").append(reason).append("\n");
        }
        facts.append("Pre-visit form: ").append(Boolean.TRUE.equals(next.getIntakeCompleted()) ? "done" : "not done")
                .append("\n");
        if (!pendingLabs.isEmpty()) {
            facts.append("Lab tests waiting for results: ")
                    .append(pendingLabs.stream().map(PatientHealthBriefService::testNames).limit(3)
                            .collect(Collectors.joining("; ")))
                    .append("\n");
        }
        facts.append("Unread lab reports: ").append(unreadReports);
        return facts.toString();
    }

    /** The patient's own reason, without the system markers some bookings carry */
    private static String patientReason(String reason) {
        if (reason == null || reason.isBlank() || reason.startsWith("RESCHEDULE:")
                || "AI Assisted Booking".equalsIgnoreCase(reason.trim())) {
            return null;
        }
        String clean = reason.replaceAll("[\\r\\n]+", " ").trim();
        return clean.length() > 150 ? clean.substring(0, 150) : clean;
    }

    static List<String> parseTips(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("-") || line.startsWith("*") || line.startsWith("•"))
                .map(line -> line.replaceFirst("^[-*•]\\s*", "").replace("**", "").trim())
                .filter(line -> !line.isEmpty())
                .map(line -> line.length() > 200 ? line.substring(0, 200) : line)
                .limit(3)
                .toList();
    }

    private static List<String> standardTips(Appointment next) {
        List<String> tips = new ArrayList<>();
        if (!Boolean.TRUE.equals(next.getIntakeCompleted())) {
            tips.add("Fill in the pre-visit form so your doctor can prepare.");
        }
        tips.add("Note your symptoms: when they started and what makes them better or worse.");
        tips.add("Keep a list of your current medicines and any recent reports ready.");
        return tips;
    }

    private static String doctorName(Appointment appointment) {
        return appointment.getDoctor() != null ? "Dr. " + appointment.getDoctor().getName() : "your doctor";
    }

    private static String testNames(Booking booking) {
        if (booking.getSelectedTests() == null || booking.getSelectedTests().isEmpty()) {
            return "your lab tests";
        }
        return booking.getSelectedTests().stream().map(LabTest::getTestName).filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
}
