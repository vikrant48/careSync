package com.vikrant.careSync.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.AiBookingSuggestion;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.AppointmentDto;
import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.dto.LabTestDto;
import com.vikrant.careSync.dto.SearchRequestDto;
import com.vikrant.careSync.dto.SlotAvailabilityResponse;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.service.AppointmentService;
import com.vikrant.careSync.service.LabTestService;
import com.vikrant.careSync.service.SavedDoctorService;
import com.vikrant.careSync.service.ai.AiBookingService;
import com.vikrant.careSync.service.ai.AiClock;
import com.vikrant.careSync.service.ai.triage.EmergencyTriageService;
import com.vikrant.careSync.service.ai.triage.TriageCategory;
import com.vikrant.careSync.service.ai.triage.TriageLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The tools the patient assistant may call. Every tool runs as the logged-in patient with the app's normal checks.
 * Nothing here books, cancels or pays: those tools only show a card, and the patient confirms by clicking it.
 * Tool arguments come from the model, so every value is validated; errors go back to the model as
 * {"error": "..."} so it can explain them.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatientAgentTools implements AgentToolbox {

    static final int MAX_DOCTORS = 5;
    private static final int DOCTORS_TO_SCAN = 8;
    private static final int MAX_DAYS_AHEAD = 90;
    private static final int TIME_WINDOW_DAYS = 7;
    private static final int MAX_REASON_LENGTH = 200;

    private final AiBookingService bookingService;
    private final AppointmentService appointmentService;
    private final DoctorRepository doctorRepository;
    private final SavedDoctorService savedDoctorService;
    private final LabTestService labTestService;
    private final EmergencyTriageService triageService;
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

    @Override
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
            log.warn("Agent tool {} failed: {}", name, e.getClass().getSimpleName());
            return error("This step failed. Please try again.");
        }
    }

    private Map<String, Tool> tools() {
        if (tools == null) {
            Map<String, Tool> map = new LinkedHashMap<>();
            map.put("flag_urgent_symptoms", new Tool(
                    "Call this FIRST whenever the patient's symptoms may need emergency care now (EMERGENCY) or a "
                            + "doctor today (URGENT), or they mention wanting to harm themselves (category "
                            + "MENTAL_HEALTH). The patient then sees emergency numbers and help. Do not call it for "
                            + "routine questions.",
                    schema(Map.of(
                            "level", Map.of("type", "string", "enum", List.of("EMERGENCY", "URGENT"),
                                    "description", "EMERGENCY: act now. URGENT: see a doctor today."),
                            "category", Map.of("type", "string", "enum", List.of("MEDICAL", "MENTAL_HEALTH"),
                                    "description", "MENTAL_HEALTH for self-harm or suicidal thoughts"),
                            "reasons", Map.of("type", "array", "items", Map.of("type", "string"),
                                    "description", "1-3 short reasons, e.g. 'chest pain spreading to the arm'")
                    ), "level", "category"), this::flagUrgentSymptoms));
            map.put("list_specializations", new Tool(
                    "List the medical specializations available on CareSync, shown to the patient as choices.",
                    schema(Map.of()), args -> listSpecializations()));
            map.put("search_doctors", new Tool(
                    "Find bookable doctors. Every filter is optional. Returns up to 5 doctors with open slots "
                            + "and shows them to the patient as cards.",
                    schema(Map.of(
                            "specialization", prop("string", "Exact name from the specialization list"),
                            "name", prop("string", "Part of the doctor's name"),
                            "gender", prop("string", "Doctor's gender, e.g. Female or Male"),
                            "max_fee", prop("number", "Highest consultation fee in rupees"),
                            "verified_only", prop("boolean", "Only doctors verified by CareSync"),
                            "date", prop("string", "Day wanted, YYYY-MM-DD"),
                            "time_from", prop("string", "Earliest time, HH:mm (24h)"),
                            "time_to", prop("string", "Latest time, HH:mm (24h)"),
                            "visit_reason", prop("string", "The patient's reason for the visit, in their words")
                    )), this::searchDoctors));
            map.put("get_available_slots", new Tool(
                    "Open time slots of one doctor on one day, shown to the patient as buttons.",
                    schema(Map.of(
                            "doctor_id", prop("integer", "Doctor id from search_doctors"),
                            "date", prop("string", "YYYY-MM-DD"),
                            "time_from", prop("string", "Earliest time, HH:mm (24h)"),
                            "time_to", prop("string", "Latest time, HH:mm (24h)"),
                            "visit_reason", prop("string", "The patient's reason for the visit")
                    ), "doctor_id", "date"), this::availableSlots));
            map.put("prepare_booking", new Tool(
                    "Show a booking confirmation card for one doctor, day and time. It does NOT book: the patient "
                            + "reviews and confirms (and pays) by pressing the card's button.",
                    schema(Map.of(
                            "doctor_id", prop("integer", "Doctor id"),
                            "date", prop("string", "YYYY-MM-DD"),
                            "time", prop("string", "Slot time HH:mm exactly as returned by get_available_slots"),
                            "visit_reason", prop("string", "The patient's reason for the visit")
                    ), "doctor_id", "date", "time"), this::prepareBooking));
            map.put("list_my_appointments", new Tool(
                    "The patient's upcoming appointments, shown with Cancel and Reschedule buttons.",
                    schema(Map.of()), args -> myAppointments()));
            map.put("prepare_cancel", new Tool(
                    "Show one of the patient's appointments with its Cancel button. It does NOT cancel: the patient "
                            + "presses the button.",
                    schema(Map.of("appointment_id", prop("integer", "Appointment id from list_my_appointments")),
                            "appointment_id"), this::prepareCancel));
            map.put("prepare_reschedule", new Tool(
                    "Move one of the patient's appointments (same doctor). With new_date and new_time it shows a "
                            + "confirmation card; with only new_date it shows that day's slots; otherwise a date picker. "
                            + "Nothing changes until the patient confirms.",
                    schema(Map.of(
                            "appointment_id", prop("integer", "Appointment id from list_my_appointments"),
                            "new_date", prop("string", "YYYY-MM-DD"),
                            "new_time", prop("string", "HH:mm")
                    ), "appointment_id"), this::prepareReschedule));
            map.put("my_saved_doctors", new Tool(
                    "Doctors the patient saved (hearted), shown as cards with their next open slot.",
                    schema(Map.of()), args -> savedDoctors()));
            map.put("search_lab_tests", new Tool(
                    "Lab tests patients can book on CareSync, with prices.",
                    schema(Map.of("name", prop("string", "Part of the test name; leave out to list all"))),
                    this::searchLabTests));
            tools = map;
        }
        return tools;
    }

    // ---------------------------------------------------------------- tools

    private AiToolResult flagUrgentSymptoms(JsonNode args) {
        TriageLevel level = enumValue(TriageLevel.class, text(args, "level"), "level");
        TriageCategory category = enumValue(TriageCategory.class, text(args, "category"), "category");
        List<String> reasons = new ArrayList<>();
        args.path("reasons").forEach(r -> reasons.add(r.asText()));
        var notice = triageService.fromAi(level, category, reasons)
                .orElseThrow(() -> new IllegalArgumentException("level must be EMERGENCY or URGENT"));
        return new AiToolResult(json(Map.of("status", "The patient now sees: " + notice.getTitle()
                + ". Add one or two calm sentences; don't repeat the phone numbers.")), null, notice);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " has an unknown value");
        }
    }

    private AiToolResult listSpecializations() {
        List<String> specializations = bookingService.getAvailableSpecializations();
        return new AiToolResult(json(Map.of("specializations", specializations)), AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.SPECIALIZATIONS).specializations(specializations).build());
    }

    private AiToolResult searchDoctors(JsonNode args) {
        bookingService.currentPatient();
        String specialization = text(args, "specialization");
        if (specialization != null) {
            String wanted = specialization;
            specialization = bookingService.getAvailableSpecializations().stream()
                    .filter(s -> s.equalsIgnoreCase(wanted)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown specialization '" + wanted + "'. Use a name from the specialization list."));
        }
        LocalDate date = optionalDate(args, "date");
        String from = optionalTime(args, "time_from");
        String to = optionalTime(args, "time_to");
        BigDecimal maxFee = args.hasNonNull("max_fee") ? BigDecimal.valueOf(args.get("max_fee").asDouble()) : null;
        boolean verifiedOnly = args.path("verified_only").asBoolean(false);

        List<Doctor> candidates = doctorRepository.searchDoctorsDynamic(SearchRequestDto.builder()
                .query(text(args, "name"))
                .specialization(specialization)
                .gender(text(args, "gender"))
                .verified(verifiedOnly ? Boolean.TRUE : null)
                .page(0).size(40).build());

        List<AiBookingSuggestion.DoctorSuggestion> cards = new ArrayList<>();
        List<Map<String, Object>> found = new ArrayList<>();
        int scanned = 0;
        for (Doctor doctor : candidates) {
            if (cards.size() >= MAX_DOCTORS || scanned >= DOCTORS_TO_SCAN) {
                break;
            }
            if (!doctor.canAcceptAppointments() || (maxFee != null && (doctor.getConsultationFees() == null
                    || doctor.getConsultationFees().compareTo(maxFee) > 0))) {
                continue;
            }
            scanned++;
            AiBookingSuggestion.DoctorSuggestion card = bookingService.mapToDoctorSuggestion(doctor);
            List<String> daySlots = null;
            if (date != null) {
                daySlots = slotsBetween(doctor.getId(), date, from, to);
                if (daySlots.isEmpty()) {
                    continue;
                }
                card.setRecommendedDate(date.toString());
                card.setRecommendedSlot(daySlots.get(0));
            } else if (from != null || to != null) {
                // Time of day without a date: first matching slot in the coming week
                for (int d = 0; d < TIME_WINDOW_DAYS && card.getRecommendedSlot() == null; d++) {
                    LocalDate day = clock.today().plusDays(d);
                    List<String> slots = slotsBetween(doctor.getId(), day, from, to);
                    if (!slots.isEmpty()) {
                        card.setRecommendedDate(day.toString());
                        card.setRecommendedSlot(slots.get(0));
                    }
                }
                if (card.getRecommendedSlot() == null) {
                    continue;
                }
            } else {
                card = bookingService.doctorWithFirstSlot(doctor);
                if (card.getRecommendedSlot() == null) {
                    continue;
                }
            }
            cards.add(card);
            Map<String, Object> row = doctorRow(doctor, card);
            if (daySlots != null) {
                row.put("open_slots_that_day", daySlots.size() > 8 ? daySlots.subList(0, 8) : daySlots);
            }
            found.add(row);
        }

        if (found.isEmpty()) {
            return AiToolResult.data(json(Map.of("doctors", List.of(),
                    "note", "No bookable doctor matches these filters. Suggest loosening a filter.")));
        }
        return new AiToolResult(json(Map.of("doctors", found)), AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.DOCTORS)
                .doctors(cards)
                .reason(reason(args))
                .build());
    }

    private AiToolResult availableSlots(JsonNode args) {
        bookingService.currentPatient();
        Doctor doctor = bookingService.bookableDoctor(requiredId(args, "doctor_id"));
        LocalDate date = requiredDate(args, "date");
        SlotAvailabilityResponse availability = appointmentService.getAvailableSlots(doctor.getId(), date.toString());
        if (availability.isOnLeave()) {
            throw new IllegalStateException("The doctor is on leave that day. Try another date.");
        }
        List<String> slots = filterTimes(availability.getAvailableSlots(), optionalTime(args, "time_from"),
                optionalTime(args, "time_to"));
        if (slots.isEmpty()) {
            return AiToolResult.data(json(Map.of("doctor_id", doctor.getId(), "date", date.toString(),
                    "slots", List.of(), "note", "No open slots in that time. Try another day or time.")));
        }
        return new AiToolResult(json(Map.of("doctor_id", doctor.getId(), "doctor", doctor.getName(),
                "date", date.toString(), "slots", slots)), AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.SLOTS)
                .doctorId(doctor.getId()).doctorName(doctor.getName()).date(date.toString())
                .slots(slots).reason(reason(args))
                .build());
    }

    private AiToolResult prepareBooking(JsonNode args) {
        Long doctorId = requiredId(args, "doctor_id");
        LocalDate date = requiredDate(args, "date");
        String time = requiredTime(args, "time");
        AiChatResponse confirmation = bookingService.prepareConfirmation(doctorId, date.toString(), time, reason(args));
        AiBookingSuggestion card = confirmation.getSuggestion();
        return new AiToolResult(json(Map.of(
                "status", "Confirmation card shown. The patient must press Confirm to book and pay.",
                "doctor", card.getDoctorName(), "date", card.getDate(), "time", card.getSlot(),
                "fee", card.getConsultationFee() == null ? "not set" : card.getConsultationFee())), card);
    }

    private AiToolResult myAppointments() {
        Patient patient = bookingService.currentPatient();
        List<Appointment> upcoming = appointmentService.getUpcomingPatientAppointments(patient.getId());
        if (upcoming.isEmpty()) {
            return AiToolResult.data(json(Map.of("appointments", List.of(), "note", "No upcoming appointments.")));
        }
        return new AiToolResult(json(Map.of("appointments", upcoming.stream().map(this::appointmentRow).toList())),
                appointmentsCard(upcoming));
    }

    private AiToolResult prepareCancel(JsonNode args) {
        Appointment appointment = ownUpcomingAppointment(requiredId(args, "appointment_id"));
        return new AiToolResult(json(Map.of(
                "status", "Appointment shown with its Cancel button. It is cancelled only if the patient presses it.",
                "appointment", appointmentRow(appointment))), appointmentsCard(List.of(appointment)));
    }

    private AiToolResult prepareReschedule(JsonNode args) {
        Appointment appointment = ownUpcomingAppointment(requiredId(args, "appointment_id"));
        Long doctorId = appointment.getDoctor().getId();
        LocalDate newDate = optionalDate(args, "new_date");
        String newTime = optionalTime(args, "new_time");
        String reason = "RESCHEDULE:" + appointment.getId();

        AiChatResponse response;
        if (newDate != null && newTime != null) {
            response = bookingService.prepareConfirmation(doctorId, newDate.toString(), newTime, reason);
        } else if (newDate != null) {
            response = bookingService.handleDateSelection(
                    "ACTION_SELECT_DATE_" + doctorId + "_" + newDate + "_" + reason);
        } else {
            response = bookingService.handleStartReschedule("ACTION_START_RESCHEDULE_" + appointment.getId());
        }
        if (!response.isSuccess() || response.getSuggestion() == null) {
            throw new IllegalStateException(response.getResponse() == null ? "Cannot reschedule this appointment."
                    : response.getResponse().replace("⚠️ ", ""));
        }
        return new AiToolResult(json(Map.of("status", response.getResponse(),
                "appointment", appointmentRow(appointment))), response.getSuggestion());
    }

    private AiToolResult savedDoctors() {
        Patient patient = bookingService.currentPatient();
        List<Long> ids = savedDoctorService.list(patient.getId()).stream().map(DoctorDto::getId).toList();
        if (ids.isEmpty()) {
            return AiToolResult.data(json(Map.of("doctors", List.of(), "note", "The patient has no saved doctors.")));
        }
        Map<Long, Doctor> byId = doctorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Doctor::getId, Function.identity()));
        List<AiBookingSuggestion.DoctorSuggestion> cards = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long id : ids) {
            Doctor doctor = byId.get(id);
            if (doctor == null || cards.size() >= MAX_DOCTORS) {
                continue;
            }
            AiBookingSuggestion.DoctorSuggestion card = bookingService.doctorWithFirstSlot(doctor);
            cards.add(card);
            rows.add(doctorRow(doctor, card));
        }
        return new AiToolResult(json(Map.of("doctors", rows)), AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.DOCTORS).doctors(cards).build());
    }

    private AiToolResult searchLabTests(JsonNode args) {
        String name = text(args, "name");
        List<LabTestDto> tests = name == null ? labTestService.getAllActiveLabTests()
                : labTestService.searchLabTestsByName(name);
        List<Map<String, Object>> rows = tests.stream()
                .filter(t -> !Boolean.FALSE.equals(t.getIsActive()))
                .limit(15)
                .map(t -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", t.getTestName());
                    row.put("price", t.getPrice());
                    if (t.getDescription() != null) {
                        row.put("about", truncate(t.getDescription(), 120));
                    }
                    return row;
                })
                .toList();
        return AiToolResult.data(json(Map.of("lab_tests", rows,
                "how_to_book", "Patients book lab tests from the Lab Tests page.")));
    }

    // ---------------------------------------------------------------- helpers

    private Appointment ownUpcomingAppointment(Long appointmentId) {
        Patient patient = bookingService.currentPatient();
        if (!bookingService.ownsAppointment(appointmentId)) {
            throw new IllegalArgumentException("That is not one of the patient's appointments.");
        }
        return appointmentService.getUpcomingPatientAppointments(patient.getId()).stream()
                .filter(a -> a.getId().equals(appointmentId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Only upcoming appointments can be changed."));
    }

    private AiBookingSuggestion appointmentsCard(List<Appointment> appointments) {
        return AiBookingSuggestion.builder()
                .type(AiBookingSuggestion.SuggestionType.MY_APPOINTMENTS)
                .appointments(appointments.stream().map(AppointmentDto::new).toList())
                .build();
    }

    private Map<String, Object> appointmentRow(Appointment a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appointment_id", a.getId());
        row.put("doctor", a.getDoctor() != null ? a.getDoctor().getName() : null);
        row.put("specialization", a.getDoctor() != null ? a.getDoctor().getSpecialization() : null);
        row.put("date", a.getAppointmentDateTime().toLocalDate().toString());
        row.put("time", a.getAppointmentDateTime().toLocalTime().toString().substring(0, 5));
        row.put("status", a.getStatus() != null ? a.getStatus().name() : null);
        return row;
    }

    private Map<String, Object> doctorRow(Doctor doctor, AiBookingSuggestion.DoctorSuggestion card) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("doctor_id", doctor.getId());
        row.put("name", doctor.getName());
        row.put("specialization", doctor.getSpecialization());
        row.put("fee", doctor.getConsultationFees());
        row.put("verified", Boolean.TRUE.equals(doctor.getIsVerified()));
        row.put("experience_years", card.getExperience());
        row.put("languages", doctor.getLanguages());
        row.put("gender", doctor.getGender());
        if (card.getRecommendedSlot() != null) {
            row.put("first_open_slot", Map.of("date", card.getRecommendedDate(), "time", card.getRecommendedSlot()));
        }
        return row;
    }

    private List<String> slotsBetween(Long doctorId, LocalDate date, String from, String to) {
        SlotAvailabilityResponse availability = appointmentService.getAvailableSlots(doctorId, date.toString());
        if (availability == null || availability.isOnLeave()) {
            return List.of();
        }
        return filterTimes(availability.getAvailableSlots(), from, to);
    }

    static List<String> filterTimes(List<String> slots, String from, String to) {
        if (slots == null) {
            return List.of();
        }
        return slots.stream().filter(Objects::nonNull)
                .filter(s -> from == null || s.compareTo(from) >= 0)
                .filter(s -> to == null || s.compareTo(to) <= 0)
                .toList();
    }

    private String reason(JsonNode args) {
        String reason = text(args, "visit_reason");
        if (reason == null) {
            return "AI Assisted Booking";
        }
        String clean = reason.replaceAll("[\\r\\n]+", " ").trim();
        // "RESCHEDULE:" is reserved for reschedules the server sets up itself
        clean = clean.replaceAll("(?i)^reschedule:", "");
        return truncate(clean, MAX_REASON_LENGTH);
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

    LocalDate optionalDate(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be a date like 2026-01-31");
        }
        LocalDate today = clock.today();
        if (date.isBefore(today)) {
            throw new IllegalArgumentException("That date is in the past. Today is " + today + ".");
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException("Bookings can be made up to " + MAX_DAYS_AHEAD + " days ahead.");
        }
        return date;
    }

    private LocalDate requiredDate(JsonNode args, String field) {
        LocalDate date = optionalDate(args, field);
        if (date == null) {
            throw new IllegalArgumentException(field + " is required (YYYY-MM-DD)");
        }
        return date;
    }

    static String optionalTime(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) {
            return null;
        }
        if (!value.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException(field + " must be a 24-hour time like 17:30");
        }
        return value;
    }

    private static String requiredTime(JsonNode args, String field) {
        String time = optionalTime(args, field);
        if (time == null) {
            throw new IllegalArgumentException(field + " is required (HH:mm)");
        }
        return time;
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

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
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
