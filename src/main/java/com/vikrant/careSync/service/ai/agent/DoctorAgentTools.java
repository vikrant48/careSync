package com.vikrant.careSync.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Vital;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.VitalRepository;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.AuditService;
import com.vikrant.careSync.service.ai.AiClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only tools of the doctor co-pilot. Every tool runs as the logged-in doctor and only reaches patients the
 * doctor treats (AccessGuard). Each record read is audited. Results carry the date of every fact so the answer can
 * cite it, and never contain contact details (phone, email, address) or the date of birth (age is enough).
 * Nothing here changes data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DoctorAgentTools implements AgentToolbox {

    static final int MAX_PATIENTS = 20;
    static final int MAX_ROWS = 10;
    private static final int MAX_TEXT = 300;
    private static final int SCHEDULE_DAYS_RANGE = 30;
    private static final int NEXT_PATIENT_DAYS = 7;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CurrentUserService currentUserService;
    private final AccessGuard accessGuard;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentIntakeRepository intakeRepository;
    private final VitalRepository vitalRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final DocumentRepository documentRepository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final AiClock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    private record Tool(String description, Map<String, Object> parameters, Function<JsonNode, AiToolResult> run) {
    }

    private Map<String, Tool> tools;

    @Override
    public List<Map<String, Object>> definitions() {
        return tools().entrySet().stream()
                .map(e -> Map.<String, Object>of("type", "function", "function", Map.of(
                        "name", e.getKey(),
                        "description", e.getValue().description(),
                        "parameters", e.getValue().parameters())))
                .collect(Collectors.toList());
    }

    /** Read-only transaction: records and their linked rows load inside it, on the agent's worker thread too */
    @Override
    @Transactional(readOnly = true)
    public AiToolResult execute(String name, String argumentsJson) {
        Tool tool = tools().get(name);
        if (tool == null) {
            return error("Unknown tool: " + name);
        }
        JsonNode args;
        try {
            args = argumentsJson == null || argumentsJson.isBlank() ? mapper.createObjectNode()
                    : mapper.readTree(argumentsJson);
        } catch (Exception e) {
            return error("Arguments were not valid JSON");
        }
        try {
            return tool.run().apply(args);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("Doctor tool {} failed: {}", name, e.getClass().getSimpleName());
            return error("This step failed. Please try again.");
        }
    }

    private Map<String, Tool> tools() {
        if (tools == null) {
            Map<String, Tool> map = new LinkedHashMap<>();
            map.put("list_my_patients", new Tool(
                    "The doctor's own patients (most recent first) with patient_id, age, gender and last "
                            + "appointment. Use it to find a patient_id from a name.",
                    schema(Map.of("name", prop("string", "Part of the patient's name; leave out to list all"))),
                    this::listMyPatients));
            map.put("get_patient_summary", new Tool(
                    "Overview of one patient: age, gender, blood group, conditions the patient reported, number of "
                            + "visits, last visit, latest vitals.",
                    schema(Map.of("patient_id", prop("integer", "Patient id")), "patient_id"),
                    this::patientSummary));
            map.put("get_recent_vitals", new Tool(
                    "The patient's recorded vitals, newest first, each with its date.",
                    schema(Map.of(
                            "patient_id", prop("integer", "Patient id"),
                            "limit", prop("integer", "How many readings, 1-10 (default 5)")
                    ), "patient_id"), this::recentVitals));
            map.put("get_medical_history", new Tool(
                    "The patient's finalised visit records, newest first: date, symptoms, diagnosis, treatment, "
                            + "medicines and doses.",
                    schema(Map.of(
                            "patient_id", prop("integer", "Patient id"),
                            "limit", prop("integer", "How many visits, 1-10 (default 5)")
                    ), "patient_id"), this::medicalHistory));
            map.put("list_lab_reports", new Tool(
                    "The patient's uploaded lab reports and lab test bookings, with dates. Lists the files; it "
                            + "cannot read the values inside a report.",
                    schema(Map.of("patient_id", prop("integer", "Patient id")), "patient_id"),
                    this::labReports));
            map.put("get_schedule", new Tool(
                    "The doctor's appointments on one day (default today), with time, patient, reason and status.",
                    schema(Map.of("date", prop("string", "YYYY-MM-DD; leave out for today"))),
                    this::schedule));
            map.put("brief_next_patient", new Tool(
                    "Brief on the doctor's next appointment: the patient, the reason, the pre-visit form (complaint, "
                            + "medicines, allergies, red flags), latest vitals and recent visits.",
                    schema(Map.of()), args -> briefNextPatient()));
            tools = map;
        }
        return tools;
    }

    // ---------------------------------------------------------------- tools

    private AiToolResult listMyPatients(JsonNode args) {
        AuthenticatedUser doctor = currentDoctor();
        String name = text(args, "name");
        List<Object[]> rows = appointmentRepository.findTreatedPatientsWithLastAppointment(doctor.id());
        Map<Long, Patient> patients = patientRepository.findAllById(
                rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        List<Map<String, Object>> found = new ArrayList<>();
        for (Object[] row : rows) {
            Patient patient = patients.get((Long) row[0]);
            if (patient == null || (name != null
                    && !patient.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))) {
                continue;
            }
            Map<String, Object> item = patientBasics(patient);
            item.put("last_appointment", day((LocalDateTime) row[1]));
            found.add(item);
            if (found.size() >= MAX_PATIENTS) {
                break;
            }
        }
        audit(doctor, "list_my_patients", "all");
        if (found.isEmpty()) {
            return AiToolResult.data(json(Map.of("patients", List.of(),
                    "note", name == null ? "You have no patients yet." : "None of your patients matches that name.")));
        }
        return AiToolResult.data(json(Map.of("patients", found)));
    }

    private AiToolResult patientSummary(JsonNode args) {
        Patient patient = ownPatient(args, "get_patient_summary");
        Map<String, Object> summary = patientBasics(patient);
        summary.put("blood_group", patient.getBloodGroup());
        if (patient.getIllnessDetails() != null && !patient.getIllnessDetails().isBlank()) {
            summary.put("conditions_reported_by_patient", truncate(patient.getIllnessDetails()));
        }
        List<MedicalHistory> visits = finalisedVisits(patient.getId());
        summary.put("finalised_visits", visits.size());
        if (!visits.isEmpty()) {
            MedicalHistory last = visits.get(0);
            summary.put("last_visit", Map.of("date", day(last.getVisitDate()),
                    "diagnosis", orNotRecorded(last.getDiagnosis())));
        }
        vitalRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()).stream().findFirst()
                .ifPresent(v -> summary.put("latest_vitals", vitalRow(v)));
        return AiToolResult.data(json(summary));
    }

    private AiToolResult recentVitals(JsonNode args) {
        Patient patient = ownPatient(args, "get_recent_vitals");
        List<Map<String, Object>> rows = vitalRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId())
                .stream().limit(limit(args)).map(DoctorAgentTools::vitalRow).toList();
        return AiToolResult.data(json(rows.isEmpty()
                ? Map.of("patient_id", patient.getId(), "vitals", List.of(), "note", "No vitals recorded.")
                : Map.of("patient_id", patient.getId(), "vitals", rows)));
    }

    private AiToolResult medicalHistory(JsonNode args) {
        Patient patient = ownPatient(args, "get_medical_history");
        List<Map<String, Object>> rows = finalisedVisits(patient.getId()).stream()
                .limit(limit(args)).map(this::visitRow).toList();
        return AiToolResult.data(json(rows.isEmpty()
                ? Map.of("patient_id", patient.getId(), "visits", List.of(), "note", "No finalised visit records.")
                : Map.of("patient_id", patient.getId(), "visits", rows)));
    }

    private AiToolResult labReports(JsonNode args) {
        Patient patient = ownPatient(args, "list_lab_reports");
        List<Map<String, Object>> reports = documentRepository
                .findByPatientIdAndDocumentType(patient.getId(), Document.DocumentType.LAB_REPORT).stream()
                .limit(MAX_ROWS)
                .map(d -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uploaded", day(d.getUploadDate()));
                    row.put("file", truncate(d.getOriginalFilename()));
                    if (d.getDescription() != null && !d.getDescription().isBlank()) {
                        row.put("description", truncate(d.getDescription()));
                    }
                    return row;
                })
                .toList();
        List<Map<String, Object>> bookings = bookingRepository.findByPatientIdOrderByBookingDateDesc(patient.getId())
                .stream().limit(MAX_ROWS).map(DoctorAgentTools::labBookingRow).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patient_id", patient.getId());
        result.put("lab_reports", reports);
        result.put("lab_test_bookings", bookings);
        if (reports.isEmpty() && bookings.isEmpty()) {
            result.put("note", "No lab reports or lab test bookings.");
        }
        return AiToolResult.data(json(result));
    }

    private AiToolResult schedule(JsonNode args) {
        AuthenticatedUser doctor = currentDoctor();
        LocalDate date = scheduleDate(text(args, "date"));
        List<Appointment> appointments = appointmentRepository.findDoctorScheduleWithPatients(doctor.id(),
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        audit(doctor, "get_schedule", date.toString());
        List<Map<String, Object>> rows = appointments.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", a.getAppointmentDateTime().format(TIME));
            row.put("appointment_id", a.getId());
            row.put("patient_id", a.getPatient().getId());
            row.put("patient", a.getPatient().getName());
            row.put("reason", truncate(a.getReason()));
            row.put("status", a.getStatus() != null ? a.getStatus().name() : null);
            row.put("pre_visit_form_done", Boolean.TRUE.equals(a.getIntakeCompleted()));
            return row;
        }).toList();
        return AiToolResult.data(json(rows.isEmpty()
                ? Map.of("date", day(date), "appointments", List.of(), "note", "No appointments that day.")
                : Map.of("date", day(date), "appointments", rows)));
    }

    private AiToolResult briefNextPatient() {
        AuthenticatedUser doctor = currentDoctor();
        LocalDateTime now = LocalDateTime.now(clock.zone());
        Appointment next = appointmentRepository.findDoctorScheduleWithPatients(doctor.id(), now,
                        now.toLocalDate().plusDays(NEXT_PATIENT_DAYS + 1L).atStartOfDay()).stream()
                .filter(a -> isAwaitingVisit(a.getStatus()))
                .findFirst()
                .orElse(null);
        if (next == null) {
            audit(doctor, "brief_next_patient", "none");
            return AiToolResult.data(json(Map.of("note",
                    "No upcoming appointment in the next " + NEXT_PATIENT_DAYS + " days.")));
        }
        Patient patient = next.getPatient();
        audit(doctor, "brief_next_patient", String.valueOf(patient.getId()));

        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("appointment", Map.of(
                "appointment_id", next.getId(),
                "date", day(next.getAppointmentDateTime()),
                "time", next.getAppointmentDateTime().format(TIME),
                "reason", orNotRecorded(truncate(next.getReason())),
                "status", next.getStatus() != null ? next.getStatus().name() : "UNKNOWN"));
        Map<String, Object> basics = patientBasics(patient);
        basics.put("blood_group", patient.getBloodGroup());
        if (patient.getIllnessDetails() != null && !patient.getIllnessDetails().isBlank()) {
            basics.put("conditions_reported_by_patient", truncate(patient.getIllnessDetails()));
        }
        brief.put("patient", basics);
        intakeRepository.findByAppointmentId(next.getId())
                .ifPresentOrElse(intake -> brief.put("pre_visit_form", intakeRow(intake)),
                        () -> brief.put("pre_visit_form", "Not filled in yet"));
        brief.put("latest_vitals", vitalRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId()).stream()
                .limit(3).map(DoctorAgentTools::vitalRow).toList());
        brief.put("recent_visits", finalisedVisits(patient.getId()).stream().limit(3).map(this::visitRow).toList());
        return AiToolResult.data(json(brief));
    }

    // ---------------------------------------------------------------- access

    private AuthenticatedUser currentDoctor() {
        return currentUserService.current()
                .filter(AuthenticatedUser::isDoctor)
                .orElseThrow(() -> new IllegalStateException("Only doctors can use these tools."));
    }

    /**
     * The patient in the arguments, only when this doctor treats them. Unknown ids get the same answer as other
     * doctors' patients, so ids cannot be probed. Every read is audited.
     */
    private Patient ownPatient(JsonNode args, String tool) {
        AuthenticatedUser doctor = currentDoctor();
        Long patientId = requiredId(args, "patient_id");
        if (!accessGuard.canAccessPatient(patientId)) {
            throw new IllegalArgumentException("patient_id " + patientId + " is not one of your patients. "
                    + "Use list_my_patients.");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("patient_id " + patientId
                        + " is not one of your patients. Use list_my_patients."));
        audit(doctor, tool, String.valueOf(patientId));
        return patient;
    }

    private void audit(AuthenticatedUser doctor, String tool, String recordId) {
        auditService.log(doctor.username(), "AI_COPILOT_READ", "Patient", recordId, "Doctor co-pilot tool: " + tool,
                null);
    }

    // ---------------------------------------------------------------- rows (no contact details)

    /** Name, age and gender only: no phone, email, address or date of birth */
    private Map<String, Object> patientBasics(Patient patient) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patient_id", patient.getId());
        row.put("name", patient.getName());
        if (patient.getDateOfBirth() != null) {
            row.put("age", Period.between(patient.getDateOfBirth(), clock.today()).getYears());
        }
        row.put("gender", patient.getGender());
        return row;
    }

    private List<MedicalHistory> finalisedVisits(Long patientId) {
        return medicalHistoryRepository.findFinalisedByPatientId(patientId).stream()
                .sorted(Comparator.comparing(MedicalHistory::getVisitDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Map<String, Object> visitRow(MedicalHistory visit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", day(visit.getVisitDate()));
        if (visit.getDoctor() != null) {
            row.put("doctor", visit.getDoctor().getName());
        }
        putIfPresent(row, "symptoms", visit.getSymptoms());
        putIfPresent(row, "diagnosis", firstPresent(visit.getDiagnosis(), visit.getAssessment()));
        putIfPresent(row, "treatment", firstPresent(visit.getTreatment(), visit.getPlan()));
        putIfPresent(row, "medicines", visit.getMedicine());
        putIfPresent(row, "doses", visit.getDoses());
        return row;
    }

    static Map<String, Object> vitalRow(Vital vital) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", day(vital.getRecordedAt()));
        if (vital.getSystolicBP() != null && vital.getDiastolicBP() != null) {
            row.put("bp_mmHg", number(vital.getSystolicBP()) + "/" + number(vital.getDiastolicBP()));
        }
        putNumber(row, "heart_rate_bpm", vital.getHeartRate());
        putNumber(row, "sugar_mg_dl", vital.getSugarLevel());
        putNumber(row, "temperature", vital.getTemperature());
        putNumber(row, "weight_kg", vital.getWeight());
        putNumber(row, "respiratory_rate", vital.getRespiratoryRate());
        return row;
    }

    private static Map<String, Object> labBookingRow(Booking booking) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", day(booking.getBookingDate()));
        row.put("tests", booking.getSelectedTests() == null ? List.of()
                : booking.getSelectedTests().stream().map(LabTest::getTestName).filter(Objects::nonNull).toList());
        row.put("status", booking.getStatus() != null ? booking.getStatus().name() : null);
        return row;
    }

    private static Map<String, Object> intakeRow(AppointmentIntake intake) {
        Map<String, Object> row = new LinkedHashMap<>();
        putIfPresent(row, "chief_complaint", intake.getChiefComplaint());
        putIfPresent(row, "symptoms", intake.getSymptoms());
        putIfPresent(row, "duration", intake.getSymptomDuration());
        putIfPresent(row, "severity", intake.getSymptomSeverity());
        putIfPresent(row, "current_medicines", intake.getCurrentMedications());
        putIfPresent(row, "allergies", intake.getAllergies());
        if (Boolean.TRUE.equals(intake.getHasRedFlags())) {
            putIfPresent(row, "red_flags", intake.getRedFlags());
        }
        if (intake.getUpdatedAt() != null || intake.getCreatedAt() != null) {
            row.put("filled_in", day(intake.getUpdatedAt() != null ? intake.getUpdatedAt() : intake.getCreatedAt()));
        }
        return row;
    }

    /** Appointments the doctor still has to see */
    private static boolean isAwaitingVisit(Appointment.Status status) {
        return status != null && switch (status) {
            case COMPLETED, NO_SHOW, NO_SHOW_PATIENT, NO_SHOW_DOCTOR, AUTO_CLOSED -> false;
            default -> true;
        };
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate scheduleDate(String value) {
        LocalDate today = clock.today();
        if (value == null) {
            return today;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be like 2026-01-31");
        }
        if (date.isBefore(today.minusDays(SCHEDULE_DAYS_RANGE)) || date.isAfter(today.plusDays(SCHEDULE_DAYS_RANGE))) {
            throw new IllegalArgumentException("Pick a date within " + SCHEDULE_DAYS_RANGE + " days of today ("
                    + today + ").");
        }
        return date;
    }

    private static int limit(JsonNode args) {
        int limit = args.path("limit").asInt(5);
        return Math.max(1, Math.min(MAX_ROWS, limit));
    }

    static String day(LocalDate date) {
        return date == null ? "date not recorded" : date.format(DAY);
    }

    static String day(LocalDateTime time) {
        return time == null ? "date not recorded" : time.toLocalDate().format(DAY);
    }

    private static String number(Double value) {
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static void putNumber(Map<String, Object> row, String field, Double value) {
        if (value != null) {
            row.put(field, number(value));
        }
    }

    private static void putIfPresent(Map<String, Object> row, String field, String value) {
        if (value != null && !value.isBlank()) {
            row.put(field, truncate(value));
        }
    }

    private static String firstPresent(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String orNotRecorded(String value) {
        return value == null || value.isBlank() ? "not recorded" : value;
    }

    private static String text(JsonNode args, String field) {
        if (!args.hasNonNull(field)) {
            return null;
        }
        String value = args.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Long requiredId(JsonNode args, String field) {
        if (!args.hasNonNull(field) || !args.get(field).canConvertToLong() || args.get(field).asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive number");
        }
        return args.get(field).asLong();
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("[\\r\\n]+", " ").trim();
        return clean.length() <= MAX_TEXT ? clean : clean.substring(0, MAX_TEXT) + "...";
    }

    private AiToolResult error(String message) {
        return AiToolResult.data(json(Map.of("error", message == null ? "This step failed." : message)));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"Could not read the result\"}";
        }
    }
}
