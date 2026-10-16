package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.AllowedActionDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.entity.AppointmentStatusLog;
import com.vikrant.careSync.event.VisitStartedEvent;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.AppointmentStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppointmentStateMachineService {

    private final RecordReadinessService recordReadinessService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusLogRepository statusLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<AllowedActionDto> getAllowedActions(Long appointmentId, User currentUser) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        validateOwnership(appointment, currentUser);

        User.Role role = currentUser.getRole();
        Appointment.Status status = appointment.getStatus();
        List<AllowedActionDto> actions = new ArrayList<>();

        if (role == User.Role.PATIENT) {
            getPatientAllowedActions(appointment, status, actions);
        } else if (role == User.Role.DOCTOR) {
            getDoctorAllowedActions(appointment, status, actions);
        } else if (role == User.Role.ADMIN) {
            actions.add(new AllowedActionDto("FORCE_CANCEL", "Force Cancel", "fa-solid fa-ban", true, null, 0, true));
            actions.add(new AllowedActionDto("FORCE_COMPLETE", "Force Complete", "fa-solid fa-check-double", true, null,
                    7, false));
        }

        return actions;
    }

    private final com.vikrant.careSync.repository.AppointmentIntakeRepository intakeRepository;
    private final com.vikrant.careSync.service.ai.AiClinicalService aiClinicalService;

    private void getPatientAllowedActions(Appointment appointment, Appointment.Status status,
            List<AllowedActionDto> actions) {
        if (status == Appointment.Status.BOOKED) {
            actions.add(new AllowedActionDto("CONFIRM", "Confirm Booking", "fa-solid fa-check", true, null, 1, true));
            actions.add(new AllowedActionDto("RESCHEDULE", "Reschedule Appointment", "fa-solid fa-calendar-days", true,
                    null, 1, false));
            actions.add(new AllowedActionDto("CANCEL", "Cancel Appointment", "fa-solid fa-ban", true, null, 1, false));
        } else if (status == Appointment.Status.CONFIRMED || status == Appointment.Status.SCHEDULED) {
            actions.add(new AllowedActionDto("CANCEL", "Cancel Appointment", "fa-solid fa-ban", true, null, 2, false));
        } else if (status == Appointment.Status.INTAKE || status == Appointment.Status.INTAKE_COMPLETED) {
            actions.add(new AllowedActionDto("SUBMIT_INTAKE", "Fill Pre-Intake Form Now", "fa-solid fa-notes-medical",
                    true, null, 3, true));
            actions.add(new AllowedActionDto("FILL_WITH_DOCTOR", "Fill Intake with Doctor", "fa-solid fa-user-doctor",
                    true, null, 3, false));
        } else if (status == Appointment.Status.WAITING_ROOM || status == Appointment.Status.READY_FOR_VISIT) {
            boolean isConfirmed = appointment.getIntake() != null
                    && Boolean.TRUE.equals(appointment.getIntake().getIsConfirmedByPatient());
            actions.add(new AllowedActionDto("CONFIRM_INTAKE",
                    isConfirmed ? "Intake Confirmed" : "Confirm Intake with Doctor", "fa-solid fa-file-signature",
                    !isConfirmed, isConfirmed ? "Already confirmed" : null, 4, !isConfirmed));
            actions.add(new AllowedActionDto("DEVICE_TEST", "Check Camera & Mic", "fa-solid fa-video", true, null, 4,
                    false));
            actions.add(new AllowedActionDto("JOIN_VIDEO", "Join Waiting Room", "fa-solid fa-door-open", true, null, 4,
                    false));
            actions.add(new AllowedActionDto("CHAT", "Chat with Doctor", "fa-solid fa-comments", true, null, 4, false));
        } else if (status == Appointment.Status.IN_PROGRESS) {
            actions.add(new AllowedActionDto("JOIN_VIDEO", "Join Video Consultation", "fa-solid fa-video", true, null,
                    5, true));
            actions.add(new AllowedActionDto("CHAT", "Chat with Doctor", "fa-solid fa-comments", true, null, 5, false));
        } else if (status == Appointment.Status.COMPLETED) {
            // Completed appointments have no process pipeline actions
        }
    }

    private void getDoctorAllowedActions(Appointment appointment, Appointment.Status status,
            List<AllowedActionDto> actions) {
        if (status == Appointment.Status.BOOKED) {
            actions.add(new AllowedActionDto("CANCEL", "Cancel Booking", "fa-solid fa-ban", true, null, 1, false));
        } else if (status == Appointment.Status.CONFIRMED || status == Appointment.Status.SCHEDULED) {
            actions.add(new AllowedActionDto("ACCEPT", "Accept Appointment", "fa-solid fa-check-circle", true, null, 2,
                    true));
            actions.add(
                    new AllowedActionDto("REJECT", "Reject Appointment", "fa-solid fa-xmark", true, null, 2, false));
        } else if (status == Appointment.Status.INTAKE || status == Appointment.Status.INTAKE_COMPLETED) {
            actions.add(new AllowedActionDto("VIEW_INTAKE", "View Pre-Intake", "fa-solid fa-clipboard-user", true, null,
                    3, true));
        } else if (status == Appointment.Status.WAITING_ROOM || status == Appointment.Status.READY_FOR_VISIT) {
            boolean isConfirmed = appointment.getIntake() != null
                    && Boolean.TRUE.equals(appointment.getIntake().getIsConfirmedByPatient());
            if (isConfirmed) {
                actions.add(new AllowedActionDto("START_PROGRESS", "Start Consultation", "fa-solid fa-stethoscope",
                        true, null, 4, true));
            } else {
                actions.add(new AllowedActionDto("START_PROGRESS", "Start Consultation", "fa-solid fa-stethoscope",
                        false, "Waiting for the patient to confirm the intake", 4, false));
                actions.add(new AllowedActionDto("OVERRIDE_START_PROGRESS", "Override & Start Consultation",
                        "fa-solid fa-shield-halved", true, null, 4, false));
            }
            actions.add(new AllowedActionDto("EDIT_INTAKE", "Edit Intake with Patient", "fa-solid fa-pen-to-square",
                    true, null, 4, false));
            actions.add(new AllowedActionDto("OPEN_INTAKE", "Preview Patient Intake", "fa-solid fa-notes-medical", true,
                    null, 4, false));
            actions.add(
                    new AllowedActionDto("CHAT", "Chat with Patient", "fa-solid fa-comments", true, null, 4, false));
        } else if (status == Appointment.Status.IN_PROGRESS) {
            actions.add(new AllowedActionDto("JOIN_VIDEO", "Join Video", "fa-solid fa-video", true, null, 5, true));
            actions.add(
                    new AllowedActionDto("CHAT", "Chat with Patient", "fa-solid fa-comments", true, null, 5, false));
            actions.add(new AllowedActionDto("ADD_MEDICAL_RECORD", "Proceed to Medical Record Stage",
                    "fa-solid fa-file-medical-step",
                    true, null, 5, false));
        } else if (status == Appointment.Status.MEDICAL_RECORD || status == Appointment.Status.REPORT_DRAFTED) {
            com.vikrant.careSync.dto.RecordReadinessDto readiness = recordReadinessService
                    .checkReadiness(appointment.getId());

            actions.add(new AllowedActionDto("AI_SOAP", "Ambient AI Scribe Assistant",
                    "fa-solid fa-wand-magic-sparkles", true, null, 6, true));
            actions.add(new AllowedActionDto("EDIT_RECORD", "Add / Edit Medical Record", "fa-solid fa-pen-to-square",
                    true, null, 6, false));
            actions.add(new AllowedActionDto("PREVIEW_PDF", "Preview Draft PDF", "fa-solid fa-file-pdf", true, null, 6,
                    false));

            String disabledReason = readiness.isReady() ? null : String.join("; ", readiness.getReasons());
            actions.add(new AllowedActionDto("MOVE_TO_COMPLETE", "Move to Complete Stage",
                    "fa-solid fa-arrow-right-to-bracket",
                    readiness.isReady(), disabledReason, 6, readiness.isReady()));
        } else if (status == Appointment.Status.READY_TO_COMPLETE) {
            actions.add(new AllowedActionDto("PREVIEW_AND_COMPLETE", "Preview & E-Sign Visit",
                    "fa-solid fa-signature", true, null, 7, true));
            actions.add(new AllowedActionDto("BACK_TO_MEDICAL_RECORD", "Back to Medical Record Stage",
                    "fa-solid fa-rotate-left", true, null, 7, false));
        } else if (status == Appointment.Status.COMPLETED) {
            // Completed appointments have no process pipeline actions
        }
    }

    // ---- Workflow transition rules -------------------------------------------------------------

    private static final Set<Appointment.Status> PRE_ACCEPT = EnumSet.of(
            Appointment.Status.BOOKED, Appointment.Status.REQUESTED,
            Appointment.Status.CONFIRMED, Appointment.Status.SCHEDULED);
    private static final Set<Appointment.Status> INTAKE_STAGE = EnumSet.of(
            Appointment.Status.INTAKE, Appointment.Status.INTAKE_COMPLETED);
    private static final Set<Appointment.Status> WAITING_STAGE = EnumSet.of(
            Appointment.Status.WAITING_ROOM, Appointment.Status.READY_FOR_VISIT);
    private static final Set<Appointment.Status> RECORD_STAGE = EnumSet.of(
            Appointment.Status.MEDICAL_RECORD, Appointment.Status.REPORT_DRAFTED);
    /** Completed or terminal: no action may change these appointments any more. */
    static final Set<Appointment.Status> CLOSED = EnumSet.of(
            Appointment.Status.COMPLETED, Appointment.Status.CANCELLED, Appointment.Status.CANCELLED_BY_PATIENT,
            Appointment.Status.CANCELLED_BY_DOCTOR, Appointment.Status.REJECTED, Appointment.Status.NO_SHOW,
            Appointment.Status.NO_SHOW_PATIENT, Appointment.Status.NO_SHOW_DOCTOR, Appointment.Status.AUTO_CLOSED);

    /** Actions that only open a screen; they never change the appointment. */
    private static final Set<String> VIEW_ACTIONS = Set.of(
            "CHAT", "VIEW_RECORD", "VIEW_INTAKE", "OPEN_INTAKE", "EDIT_RECORD", "PREVIEW_PDF",
            "PREVIEW_AND_COMPLETE", "DOWNLOAD_RX", "SUBMIT_FEEDBACK", "RESCHEDULE");

    private static Set<Appointment.Status> union(Set<Appointment.Status> a, Set<Appointment.Status> b) {
        Set<Appointment.Status> result = EnumSet.copyOf(a);
        result.addAll(b);
        return result;
    }

    private static void requireRole(User currentUser, String action, User.Role... roles) {
        for (User.Role role : roles) {
            if (currentUser.getRole() == role) {
                return;
            }
        }
        throw new SecurityException("Your role cannot perform " + action);
    }

    private static void requireStatus(Appointment appointment, String action, Set<Appointment.Status> allowed) {
        if (!allowed.contains(appointment.getStatus())) {
            throw new IllegalStateException(
                    "Cannot perform " + action + " while the appointment is " + appointment.getStatus());
        }
    }

    private static void moveTo(Appointment appointment, Appointment.Status newStatus, String actor) {
        appointment.setStatus(newStatus);
        appointment.setStatusChangedAt(LocalDateTime.now());
        appointment.setStatusChangedBy(actor);
        if (Appointment.isTerminalStatus(newStatus) || newStatus == Appointment.Status.REJECTED) {
            appointment.setIsActive(false);
        }
    }

    /**
     * Runs one workflow action. Every action has the roles allowed to run it and the statuses it may
     * start from, so a visit cannot be moved backwards, skipped ahead, or reopened once closed.
     * Role violations raise SecurityException (403); invalid transitions IllegalStateException (409).
     */
    public Appointment executeAction(Long appointmentId, String actionKey, User currentUser,
            Map<String, Object> payload) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        validateOwnership(appointment, currentUser);

        String action = actionKey.toUpperCase();
        Appointment.Status oldStatus = appointment.getStatus();
        String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;
        String actor = currentUser.getUsername();
        boolean isPatient = currentUser.getRole() == User.Role.PATIENT;

        if (!VIEW_ACTIONS.contains(action) && CLOSED.contains(oldStatus)) {
            throw new IllegalStateException("This appointment is " + oldStatus + " and can no longer be changed");
        }

        switch (action) {
            case "CONFIRM":
                requireRole(currentUser, action, User.Role.PATIENT, User.Role.ADMIN);
                if (oldStatus == Appointment.Status.CONFIRMED || oldStatus == Appointment.Status.SCHEDULED) {
                    break; // already confirmed, idempotent success
                }
                requireStatus(appointment, action, EnumSet.of(Appointment.Status.BOOKED));
                moveTo(appointment, Appointment.Status.CONFIRMED, actor);
                break;

            case "ACCEPT":
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                if (INTAKE_STAGE.contains(oldStatus) || WAITING_STAGE.contains(oldStatus)
                        || oldStatus == Appointment.Status.IN_PROGRESS) {
                    break; // already accepted, idempotent success
                }
                requireStatus(appointment, action, PRE_ACCEPT);
                moveTo(appointment, Appointment.Status.INTAKE, actor);
                break;

            case "REJECT":
                requireRole(currentUser, action, User.Role.DOCTOR);
                requireStatus(appointment, action, PRE_ACCEPT);
                if (reason == null || reason.trim().isEmpty()) {
                    throw new IllegalArgumentException("Rejection reason is required");
                }
                appointment.setCancellationReason(reason);
                moveTo(appointment, Appointment.Status.REJECTED, actor);
                break;

            case "CANCEL":
                if (isPatient) {
                    if (oldStatus != Appointment.Status.BOOKED && oldStatus != Appointment.Status.CONFIRMED) {
                        throw new IllegalStateException("Patient cannot cancel appointment once doctor has accepted.");
                    }
                } else {
                    // Doctors may cancel until the consultation starts; afterwards the visit must be completed
                    requireStatus(appointment, action, union(union(PRE_ACCEPT, INTAKE_STAGE), WAITING_STAGE));
                }
                if (reason != null) {
                    appointment.setCancellationReason(reason);
                }
                moveTo(appointment, isPatient ? Appointment.Status.CANCELLED_BY_PATIENT
                        : Appointment.Status.CANCELLED_BY_DOCTOR, actor);
                break;

            case "SUBMIT_INTAKE":
            case "FILL_WITH_DOCTOR":
                requireRole(currentUser, action, User.Role.PATIENT, User.Role.ADMIN);
                if (WAITING_STAGE.contains(oldStatus)) {
                    break; // already in the waiting room
                }
                requireStatus(appointment, action, INTAKE_STAGE);
                if ("SUBMIT_INTAKE".equals(action)) {
                    appointment.setIntakeCompleted(true);
                }
                moveTo(appointment, Appointment.Status.WAITING_ROOM, actor);
                break;

            case "JOIN_WAITING_ROOM":
            case "JOIN_VIDEO":
            case "JOIN_ROOM":
            case "DEVICE_TEST":
                if (!isPatient) {
                    break; // a doctor joining the room does not change the patient state
                }
                requireStatus(appointment, action,
                        union(union(INTAKE_STAGE, WAITING_STAGE), EnumSet.of(Appointment.Status.IN_PROGRESS)));
                appointment.setPatientReady(true);
                if (INTAKE_STAGE.contains(oldStatus)) {
                    moveTo(appointment, Appointment.Status.WAITING_ROOM, "PATIENT_JOINED_WAITING_ROOM");
                }
                break;

            case "CONFIRM_INTAKE": {
                requireRole(currentUser, action, User.Role.PATIENT, User.Role.ADMIN);
                requireStatus(appointment, action, union(INTAKE_STAGE, WAITING_STAGE));
                com.vikrant.careSync.entity.AppointmentIntake currentIntake = intakeRepository
                        .findByAppointmentId(appointmentId)
                        .orElseGet(() -> intakeRepository.save(com.vikrant.careSync.entity.AppointmentIntake.builder()
                                .appointment(appointment)
                                .chiefComplaint("Filled during visit preparation")
                                .build()));
                currentIntake.setIsConfirmedByPatient(true);
                currentIntake.setConfirmedAt(LocalDateTime.now());
                currentIntake.setConfirmedBy(actor);
                currentIntake.setIntakeStatus("CONFIRMED");
                intakeRepository.save(currentIntake);
                appointment.setPatientReady(true);
                break;
            }

            case "EDIT_INTAKE": {
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                requireStatus(appointment, action,
                        union(union(INTAKE_STAGE, WAITING_STAGE), EnumSet.of(Appointment.Status.IN_PROGRESS)));
                com.vikrant.careSync.entity.AppointmentIntake intakeToEdit = intakeRepository
                        .findByAppointmentId(appointmentId)
                        .orElseGet(() -> com.vikrant.careSync.entity.AppointmentIntake.builder()
                                .appointment(appointment)
                                .build());
                if (payload != null) {
                    if (payload.get("chiefComplaint") != null)
                        intakeToEdit.setChiefComplaint(payload.get("chiefComplaint").toString());
                    if (payload.get("symptoms") != null)
                        intakeToEdit.setSymptoms(payload.get("symptoms").toString());
                    if (payload.get("symptomDuration") != null)
                        intakeToEdit.setSymptomDuration(payload.get("symptomDuration").toString());
                    if (payload.get("symptomSeverity") != null)
                        intakeToEdit.setSymptomSeverity(payload.get("symptomSeverity").toString());
                    if (payload.get("currentMedications") != null)
                        intakeToEdit.setCurrentMedications(payload.get("currentMedications").toString());
                    if (payload.get("allergies") != null)
                        intakeToEdit.setAllergies(payload.get("allergies").toString());
                }
                // Regenerate AI intake summary with updated clinical information
                com.vikrant.careSync.dto.AiIntakeSummaryDto updatedSummary = aiClinicalService
                        .generateStructuredIntakeSummary(
                                intakeToEdit.getChiefComplaint(),
                                intakeToEdit.getSymptoms(),
                                intakeToEdit.getSymptomDuration(),
                                intakeToEdit.getSymptomSeverity(),
                                intakeToEdit.getCurrentMedications(),
                                intakeToEdit.getAllergies());
                intakeToEdit.setAiSummary(updatedSummary.getSummaryText());
                intakeToEdit.setHasRedFlags(updatedSummary.isHasEmergencyFlags());
                intakeToEdit.setRedFlags(updatedSummary.getEmergencyMessage());
                intakeToEdit.setIntakeStatus("EDITED_BY_DOCTOR");
                // Reset patient confirmation so patient re-verifies edits
                intakeToEdit.setIsConfirmedByPatient(false);
                intakeRepository.save(intakeToEdit);
                break;
            }

            case "START_PROGRESS":
            case "OVERRIDE_START_PROGRESS": {
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                if (oldStatus == Appointment.Status.IN_PROGRESS) {
                    return appointment; // already in progress, idempotent success
                }
                requireStatus(appointment, action, union(INTAKE_STAGE, WAITING_STAGE));
                com.vikrant.careSync.entity.AppointmentIntake intakeForStart = intakeRepository
                        .findByAppointmentId(appointmentId)
                        .orElseGet(() -> intakeRepository.save(com.vikrant.careSync.entity.AppointmentIntake.builder()
                                .appointment(appointment)
                                .chiefComplaint("Filled during consultation start")
                                .intakeStatus("CONFIRMED_ON_VISIT_START")
                                .isConfirmedByPatient(true)
                                .build()));

                if (!Boolean.TRUE.equals(intakeForStart.getIsConfirmedByPatient())) {
                    intakeForStart.setIsConfirmedByPatient(true);
                    intakeForStart.setConfirmedAt(LocalDateTime.now());
                    intakeForStart.setConfirmedBy(actor);
                    intakeRepository.save(intakeForStart);
                }

                if (appointment.getVisitStartedAt() == null) {
                    appointment.setVisitStartedAt(LocalDateTime.now());
                }
                appointment.setPatientReady(true);
                moveTo(appointment, Appointment.Status.IN_PROGRESS, actor);
                eventPublisher.publishEvent(new VisitStartedEvent(this, appointment.getId(),
                        appointment.getDoctor().getId(), appointment.getPatient().getId()));
                break;
            }

            case "ADD_MEDICAL_RECORD":
            case "AI_SOAP":
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                if (RECORD_STAGE.contains(oldStatus)) {
                    break; // already in the medical record stage
                }
                requireStatus(appointment, action, EnumSet.of(Appointment.Status.IN_PROGRESS));
                moveTo(appointment, Appointment.Status.MEDICAL_RECORD, actor);
                break;

            case "MOVE_TO_COMPLETE": {
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                requireStatus(appointment, action, RECORD_STAGE);
                com.vikrant.careSync.dto.RecordReadinessDto readiness = recordReadinessService
                        .checkReadiness(appointmentId);
                if (!readiness.isReady()) {
                    throw new IllegalStateException(String.join("; ", readiness.getReasons()));
                }
                moveTo(appointment, Appointment.Status.READY_TO_COMPLETE, actor);
                break;
            }

            case "BACK_TO_MEDICAL_RECORD":
                requireRole(currentUser, action, User.Role.DOCTOR, User.Role.ADMIN);
                if (oldStatus != Appointment.Status.READY_TO_COMPLETE) {
                    throw new IllegalStateException(
                            "Can only return to Medical Record stage from Complete review stage.");
                }
                log.info("Doctor {} returning appointment {} from READY_TO_COMPLETE to MEDICAL_RECORD stage",
                        actor, appointmentId);
                moveTo(appointment, Appointment.Status.MEDICAL_RECORD, actor);
                break;

            case "FORCE_CANCEL":
                requireRole(currentUser, action, User.Role.ADMIN);
                moveTo(appointment, Appointment.Status.CANCELLED, actor);
                appointment.setIsActive(false);
                break;

            case "FORCE_COMPLETE":
                requireRole(currentUser, action, User.Role.ADMIN);
                moveTo(appointment, Appointment.Status.COMPLETED, actor);
                break;

            default:
                if (VIEW_ACTIONS.contains(action)) {
                    // View/Modal navigation actions, return current appointment state without status mutation
                    log.info("Executing non-mutating view action '{}' for appointment {}", actionKey, appointmentId);
                    break;
                }
                throw new IllegalArgumentException("Unknown action key: " + actionKey);
        }

        Appointment saved = appointmentRepository.save(appointment);

        // Audit Trail
        AppointmentStatusLog statusLog = AppointmentStatusLog.of(saved.getId(), oldStatus, saved.getStatus(), actor,
                reason != null ? reason : "Action " + actionKey + " executed by " + currentUser.getRole());
        statusLogRepository.save(statusLog);

        return saved;
    }

    private void validateOwnership(Appointment appointment, User currentUser) {
        if (currentUser.getRole() == User.Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == User.Role.DOCTOR) {
            if (appointment.getDoctor() != null && appointment.getDoctor().getUser() != null) {
                if (!appointment.getDoctor().getUser().getId().equals(currentUser.getId())) {
                    throw new SecurityException("Access denied: Appointment belongs to another doctor");
                }
            } else if (appointment.getDoctor() != null
                    && !appointment.getDoctor().getId().equals(currentUser.getId())) {
                throw new SecurityException("Access denied: Appointment belongs to another doctor");
            }
        } else if (currentUser.getRole() == User.Role.PATIENT) {
            if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
                if (!appointment.getPatient().getUser().getId().equals(currentUser.getId())) {
                    throw new SecurityException("Access denied: Appointment belongs to another patient");
                }
            } else if (appointment.getPatient() != null
                    && !appointment.getPatient().getId().equals(currentUser.getId())) {
                throw new SecurityException("Access denied: Appointment belongs to another patient");
            }
        }
    }
}
