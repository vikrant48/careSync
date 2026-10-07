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
import java.util.List;
import java.util.Map;

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

    public Appointment executeAction(Long appointmentId, String actionKey, User currentUser,
            Map<String, Object> payload) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        validateOwnership(appointment, currentUser);

        Appointment.Status oldStatus = appointment.getStatus();
        String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;

        switch (actionKey.toUpperCase()) {
            case "CONFIRM":
                if (appointment.getStatus() == Appointment.Status.CONFIRMED
                        || appointment.getStatus() == Appointment.Status.SCHEDULED) {
                    // Already confirmed, idempotent success
                    break;
                }
                if (appointment.getStatus() != Appointment.Status.BOOKED) {
                    throw new IllegalStateException("Cannot confirm booking from status: " + oldStatus);
                }
                appointment.setStatus(Appointment.Status.CONFIRMED);
                break;

            case "ACCEPT":
                if (currentUser.getRole() != User.Role.DOCTOR && currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only doctors can accept appointments");
                }
                if (oldStatus == Appointment.Status.INTAKE || oldStatus == Appointment.Status.INTAKE_COMPLETED
                        || oldStatus == Appointment.Status.WAITING_ROOM || oldStatus == Appointment.Status.IN_PROGRESS
                        || oldStatus == Appointment.Status.COMPLETED) {
                    // Already accepted, idempotent success
                    break;
                }
                if (oldStatus != Appointment.Status.BOOKED && oldStatus != Appointment.Status.CONFIRMED
                        && oldStatus != Appointment.Status.SCHEDULED) {
                    throw new IllegalStateException("Cannot accept appointment from status: " + oldStatus);
                }
                appointment.setStatus(Appointment.Status.INTAKE);
                break;

            case "REJECT":
                if (currentUser.getRole() != User.Role.DOCTOR) {
                    throw new IllegalArgumentException("Only doctors can reject appointments");
                }
                if (reason == null || reason.trim().isEmpty()) {
                    throw new IllegalArgumentException("Rejection reason is required");
                }
                appointment.setStatus(Appointment.Status.REJECTED);
                appointment.setCancellationReason(reason);
                appointment.setIsActive(false);
                break;

            case "CANCEL":
                if (currentUser.getRole() == User.Role.PATIENT) {
                    if (oldStatus != Appointment.Status.BOOKED && oldStatus != Appointment.Status.CONFIRMED) {
                        throw new IllegalStateException("Patient cannot cancel appointment once doctor has accepted.");
                    }
                    appointment.setStatus(Appointment.Status.CANCELLED_BY_PATIENT);
                } else {
                    appointment.setStatus(Appointment.Status.CANCELLED_BY_DOCTOR);
                }
                if (reason != null) {
                    appointment.setCancellationReason(reason);
                }
                appointment.setIsActive(false);
                break;

            case "SUBMIT_INTAKE":
                appointment.setIntakeCompleted(true);
                appointment.setStatus(Appointment.Status.WAITING_ROOM);
                break;

            case "FILL_WITH_DOCTOR":
                appointment.setStatus(Appointment.Status.WAITING_ROOM);
                break;

            case "JOIN_WAITING_ROOM":
            case "JOIN_VIDEO":
            case "JOIN_ROOM":
            case "DEVICE_TEST":
                appointment.setPatientReady(true);
                if (appointment.canChangeStatus(Appointment.Status.WAITING_ROOM)) {
                    appointment.changeStatus(Appointment.Status.WAITING_ROOM, "PATIENT_JOINED_WAITING_ROOM");
                }
                break;

            case "CHAT":
                // In-session communication action, no status state change required
                break;

            case "CONFIRM_INTAKE":
                com.vikrant.careSync.entity.AppointmentIntake currentIntake = intakeRepository
                        .findByAppointmentId(appointmentId)
                        .orElseGet(() -> intakeRepository.save(com.vikrant.careSync.entity.AppointmentIntake.builder()
                                .appointment(appointment)
                                .chiefComplaint("Filled during visit preparation")
                                .build()));
                currentIntake.setIsConfirmedByPatient(true);
                currentIntake.setConfirmedAt(LocalDateTime.now());
                currentIntake.setConfirmedBy(currentUser.getUsername());
                currentIntake.setIntakeStatus("CONFIRMED");
                intakeRepository.save(currentIntake);
                appointment.setPatientReady(true);
                break;

            case "EDIT_INTAKE":
                if (currentUser.getRole() != User.Role.DOCTOR && currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only doctors can edit patient intake");
                }
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

            case "START_PROGRESS":
            case "OVERRIDE_START_PROGRESS":
                if (currentUser.getRole() != User.Role.DOCTOR && currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only doctors can start consultations");
                }
                if (appointment.getStatus() == Appointment.Status.IN_PROGRESS) {
                    // Already in progress, idempotent success
                    return appointment;
                }
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
                    intakeForStart.setConfirmedBy(currentUser.getUsername());
                    intakeRepository.save(intakeForStart);
                }

                if (appointment.getVisitStartedAt() == null) {
                    appointment.setVisitStartedAt(LocalDateTime.now());
                }
                appointment.setPatientReady(true);
                appointment.setStatus(Appointment.Status.IN_PROGRESS);
                eventPublisher.publishEvent(new VisitStartedEvent(this, appointment.getId(),
                        appointment.getDoctor().getId(), appointment.getPatient().getId()));
                break;

            case "ADD_MEDICAL_RECORD":
            case "AI_SOAP":
                appointment.setStatus(Appointment.Status.MEDICAL_RECORD);
                break;

            case "MOVE_TO_COMPLETE":
                if (currentUser.getRole() != User.Role.DOCTOR && currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only doctors can move appointment to complete review stage");
                }
                com.vikrant.careSync.dto.RecordReadinessDto readiness = recordReadinessService
                        .checkReadiness(appointmentId);
                if (!readiness.isReady()) {
                    throw new IllegalStateException(String.join("; ", readiness.getReasons()));
                }
                appointment.setStatus(Appointment.Status.READY_TO_COMPLETE);
                break;

            case "BACK_TO_MEDICAL_RECORD":
                if (currentUser.getRole() != User.Role.DOCTOR && currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only doctors can revert appointment to Medical Record stage");
                }
                if (oldStatus != Appointment.Status.READY_TO_COMPLETE) {
                    throw new IllegalStateException(
                            "Can only return to Medical Record stage from Complete review stage.");
                }
                log.info("Doctor {} returning appointment {} from READY_TO_COMPLETE to MEDICAL_RECORD stage",
                        currentUser.getUsername(), appointmentId);
                appointment.setStatus(Appointment.Status.MEDICAL_RECORD);
                break;

            case "FORCE_CANCEL":
                if (currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only admins can force cancel appointments");
                }
                appointment.setStatus(Appointment.Status.CANCELLED);
                appointment.setIsActive(false);
                break;

            case "FORCE_COMPLETE":
                if (currentUser.getRole() != User.Role.ADMIN) {
                    throw new IllegalArgumentException("Only admins can force complete appointments");
                }
                appointment.setStatus(Appointment.Status.COMPLETED);
                break;

            case "VIEW_RECORD":
            case "VIEW_INTAKE":
            case "OPEN_INTAKE":
            case "EDIT_RECORD":
            case "PREVIEW_PDF":
            case "PREVIEW_AND_COMPLETE":
            case "DOWNLOAD_RX":
            case "SUBMIT_FEEDBACK":
            case "RESCHEDULE":
                // View/Modal navigation actions, return current appointment state without
                // status mutation
                log.info("Executing non-mutating view action '{}' for appointment {}", actionKey, appointmentId);
                break;

            default:
                throw new IllegalArgumentException("Unknown action key: " + actionKey);
        }

        Appointment saved = appointmentRepository.save(appointment);

        // Audit Trail
        AppointmentStatusLog statusLog = AppointmentStatusLog.builder()
                .appointmentId(saved.getId())
                .previousStatus(oldStatus)
                .newStatus(saved.getStatus())
                .changedBy(currentUser.getUsername())
                .reason(reason != null ? reason : "Action " + actionKey + " executed by " + currentUser.getRole())
                .build();
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
