package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Vital;
import com.vikrant.careSync.repository.AppointmentIntakeRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.repository.VitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A patient's own health record as a FHIR R4 Bundle (HL7's international format), so it can be opened by other
 * health apps or linked to an ABHA account later. Only what a doctor signed goes in as a visit; allergies and
 * current medicines the patient reported at intake are marked as patient-reported; AI lab explanations are
 * labelled as not reviewed by a doctor. Built from plain values, like {@link PatientDataExportService}.
 */
@Service
@RequiredArgsConstructor
public class FhirExportService {

    static final String APP_SYSTEM = "https://caresync-vikrant.vercel.app/fhir";
    private static final String LOINC = "http://loinc.org";
    private static final String UCUM = "http://unitsofmeasure.org";
    private static final String OBS_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";
    private static final Pattern NOTHING_REPORTED = Pattern.compile(
            "(?i)^(none|nil|no|na|n/?a|nka|nkda|-+|no known( drug)? allerg(y|ies)|no allerg(y|ies)|no medicines?|"
                    + "no medications?|not taking any( medicines?| medications?)?)\\.?$");
    private static final Pattern LIST_MARK = Pattern.compile("^\\s*(?:[-*•]|\\d+[.)])\\s*");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentIntakeRepository intakeRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final VitalRepository vitalRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> export(Patient patient) {
        Long patientId = patient.getId();
        List<MedicalHistory> signed = medicalHistoryRepository.findByPatientIdOrderByVisitDateDesc(patientId).stream()
                .filter(record -> Boolean.TRUE.equals(record.getIsSigned()))
                .toList();
        List<AppointmentIntake> intakes = new ArrayList<>();
        for (Appointment appointment : appointmentRepository.findByPatientIdWithPatientAndDoctorDetails(patientId)) {
            intakeRepository.findByAppointmentId(appointment.getId()).ifPresent(intakes::add);
        }
        List<Document> labReports = documentRepository
                .findByPatientIdAndDocumentType(patientId, Document.DocumentType.LAB_REPORT);
        return bundle(patient, signed, vitalRepository.findByPatientIdOrderByRecordedAtDesc(patientId), intakes,
                labReports, LocalDateTime.now());
    }

    /** The whole Bundle from already-loaded data (kept separate so it can be tested without a database) */
    static Map<String, Object> bundle(Patient patient, List<MedicalHistory> signedRecords, List<Vital> vitals,
            List<AppointmentIntake> intakes, List<Document> labReports, LocalDateTime now) {
        List<Map<String, Object>> entries = new ArrayList<>();
        String patientRef = ref("Patient", patient.getId());
        entries.add(entry(patientRef, patientResource(patient)));
        if (notBlank(patient.getBloodGroup())) {
            entries.add(entry(ref("Observation", "blood-group-" + patient.getId()), bloodGroup(patient, patientRef)));
        }

        Set<Long> doctorsAdded = new LinkedHashSet<>();
        for (MedicalHistory record : signedRecords) {
            Doctor doctor = record.getDoctor();
            String doctorRef = doctor != null ? ref("Practitioner", doctor.getId()) : null;
            if (doctor != null && doctorsAdded.add(doctor.getId())) {
                entries.add(entry(doctorRef, practitioner(doctor)));
            }
            String encounterRef = ref("Encounter", record.getId());
            entries.add(entry(encounterRef, encounter(record, patientRef, doctorRef)));
            if (notBlank(record.getDiagnosis())) {
                entries.add(entry(ref("Condition", record.getId()),
                        condition(record, patientRef, encounterRef, doctorRef)));
            }
            Map<String, Object> note = visitNote(record, patientRef, encounterRef, doctorRef);
            if (note != null) {
                entries.add(entry(ref("Composition", record.getId()), note));
            }
            List<String[]> medicines = medicines(record.getMedicine(), record.getDoses());
            for (int i = 0; i < medicines.size(); i++) {
                entries.add(entry(ref("MedicationRequest", record.getId() + "-" + i),
                        medicationRequest(medicines.get(i), record, patientRef, encounterRef, doctorRef)));
            }
        }

        for (Vital vital : vitals) {
            vitalObservations(vital, patientRef).forEach((key, resource) ->
                    entries.add(entry(ref("Observation", "vital-" + vital.getId() + "-" + key), resource)));
        }

        // The same allergy or medicine is usually reported at every visit: keep the latest report of each
        List<AppointmentIntake> newestFirst = intakes.stream()
                .sorted((a, b) -> compareDesc(a.getCreatedAt(), b.getCreatedAt()))
                .toList();
        Set<String> allergiesSeen = new LinkedHashSet<>();
        Set<String> medicinesSeen = new LinkedHashSet<>();
        for (AppointmentIntake intake : newestFirst) {
            for (String allergy : items(intake.getAllergies())) {
                if (allergiesSeen.add(allergy.toLowerCase(Locale.ROOT))) {
                    entries.add(entry(ref("AllergyIntolerance", allergy.toLowerCase(Locale.ROOT)),
                            allergy(allergy, intake, patientRef)));
                }
            }
            for (String medicine : items(intake.getCurrentMedications())) {
                if (medicinesSeen.add(medicine.toLowerCase(Locale.ROOT))) {
                    entries.add(entry(ref("MedicationStatement", medicine.toLowerCase(Locale.ROOT)),
                            medicationStatement(medicine, intake, patientRef)));
                }
            }
        }

        for (Document report : labReports) {
            if (Boolean.FALSE.equals(report.getIsActive())) {
                continue;
            }
            entries.add(entry(ref("DiagnosticReport", report.getId()), labReport(report, patientRef)));
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", UUID.randomUUID().toString());
        bundle.put("meta", Map.of("lastUpdated", dateTime(now)));
        bundle.put("type", "collection");
        bundle.put("timestamp", dateTime(now));
        bundle.put("entry", entries);
        return bundle;
    }

    // ---- Resources

    private static Map<String, Object> patientResource(Patient patient) {
        Map<String, Object> r = resource("Patient");
        r.put("identifier", List.of(Map.of("system", APP_SYSTEM + "/patient-id", "value", String.valueOf(patient.getId()))));
        r.put("active", !Boolean.FALSE.equals(patient.getIsActive()));
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("use", "official");
        if (notBlank(patient.getLastName())) {
            name.put("family", patient.getLastName().trim());
        }
        if (notBlank(patient.getFirstName())) {
            name.put("given", List.of(patient.getFirstName().trim()));
        }
        r.put("name", List.of(name));
        String phone = patient.getContactInfo();
        if (notBlank(phone) && PHONE.matcher(phone.trim()).matches()) {
            r.put("telecom", List.of(Map.of("system", "phone", "value", phone.trim())));
        }
        r.put("gender", gender(patient.getGender()));
        if (patient.getDateOfBirth() != null) {
            r.put("birthDate", patient.getDateOfBirth().toString());
        }
        return r;
    }

    private static Map<String, Object> bloodGroup(Patient patient, String patientRef) {
        Map<String, Object> r = resource("Observation");
        r.put("status", "final");
        r.put("category", List.of(category("laboratory", "Laboratory")));
        r.put("code", loinc("882-1", "ABO and Rh group [Type] in Blood"));
        r.put("subject", reference(patientRef));
        r.put("valueCodeableConcept", Map.of("text", patient.getBloodGroup().trim()));
        return r;
    }

    private static Map<String, Object> practitioner(Doctor doctor) {
        Map<String, Object> r = resource("Practitioner");
        if (notBlank(doctor.getLicenseNumber())) {
            r.put("identifier", List.of(Map.of("system", APP_SYSTEM + "/doctor-license",
                    "value", doctor.getLicenseNumber().trim())));
        }
        Map<String, Object> name = new LinkedHashMap<>();
        if (notBlank(doctor.getLastName())) {
            name.put("family", doctor.getLastName().trim());
        }
        if (notBlank(doctor.getFirstName())) {
            name.put("given", List.of(doctor.getFirstName().trim()));
        }
        name.put("prefix", List.of("Dr."));
        r.put("name", List.of(name));
        if (notBlank(doctor.getSpecialization())) {
            r.put("qualification", List.of(Map.of("code", Map.of("text", doctor.getSpecialization().trim()))));
        }
        return r;
    }

    private static Map<String, Object> encounter(MedicalHistory record, String patientRef, String doctorRef) {
        Map<String, Object> r = resource("Encounter");
        r.put("status", "finished");
        r.put("class", Map.of("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code", "VR", "display", "virtual"));
        r.put("subject", reference(patientRef));
        if (doctorRef != null) {
            r.put("participant", List.of(Map.of("individual", reference(doctorRef))));
        }
        if (record.getVisitDate() != null) {
            r.put("period", Map.of("start", record.getVisitDate().toString()));
        }
        if (record.getAppointmentId() != null) {
            r.put("identifier", List.of(Map.of("system", APP_SYSTEM + "/appointment-id",
                    "value", String.valueOf(record.getAppointmentId()))));
        }
        return r;
    }

    private static Map<String, Object> condition(MedicalHistory record, String patientRef, String encounterRef,
            String doctorRef) {
        Map<String, Object> r = resource("Condition");
        r.put("verificationStatus", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/condition-ver-status", "code", "confirmed"))));
        r.put("category", List.of(Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/condition-category",
                "code", "encounter-diagnosis", "display", "Encounter Diagnosis")))));
        r.put("code", Map.of("text", record.getDiagnosis().trim()));
        r.put("subject", reference(patientRef));
        r.put("encounter", reference(encounterRef));
        if (record.getVisitDate() != null) {
            r.put("recordedDate", record.getVisitDate().toString());
        }
        if (doctorRef != null) {
            r.put("recorder", reference(doctorRef));
        }
        return r;
    }

    /** The signed SOAP note as a Composition; null when the record has no note text or no doctor */
    private static Map<String, Object> visitNote(MedicalHistory record, String patientRef, String encounterRef,
            String doctorRef) {
        List<Map<String, Object>> sections = new ArrayList<>();
        addSection(sections, "Subjective", firstNotBlank(record.getSubjective(), record.getSymptoms()));
        addSection(sections, "Objective", record.getObjective());
        addSection(sections, "Assessment", record.getAssessment());
        addSection(sections, "Plan", firstNotBlank(record.getPlan(), record.getTreatment()));
        addSection(sections, "Notes", record.getNotes());
        if (sections.isEmpty() || doctorRef == null) {
            return null;
        }
        Map<String, Object> r = resource("Composition");
        r.put("status", "final");
        r.put("type", loinc("11506-3", "Progress note"));
        r.put("subject", reference(patientRef));
        r.put("encounter", reference(encounterRef));
        r.put("date", record.getSignedAt() != null ? dateTime(record.getSignedAt())
                : record.getVisitDate() != null ? record.getVisitDate().toString() : null);
        r.values().removeIf(Objects::isNull);
        r.put("author", List.of(reference(doctorRef)));
        r.put("title", "Visit note");
        r.put("section", sections);
        return r;
    }

    private static Map<String, Object> medicationRequest(String[] medicine, MedicalHistory record, String patientRef,
            String encounterRef, String doctorRef) {
        Map<String, Object> r = resource("MedicationRequest");
        r.put("status", "unknown");
        r.put("intent", "order");
        r.put("medicationCodeableConcept", Map.of("text", medicine[0]));
        r.put("subject", reference(patientRef));
        r.put("encounter", reference(encounterRef));
        if (record.getVisitDate() != null) {
            r.put("authoredOn", record.getVisitDate().toString());
        }
        if (doctorRef != null) {
            r.put("requester", reference(doctorRef));
        }
        if (notBlank(medicine[1])) {
            r.put("dosageInstruction", List.of(Map.of("text", medicine[1])));
        }
        return r;
    }

    /** One Observation per measured value, keyed so each gets a stable id */
    private static Map<String, Map<String, Object>> vitalObservations(Vital vital, String patientRef) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (vital.getSystolicBP() != null || vital.getDiastolicBP() != null) {
            Map<String, Object> bp = observation(vital, patientRef, "vital-signs", "Vital Signs",
                    loinc("85354-9", "Blood pressure panel with all children optional"));
            List<Map<String, Object>> components = new ArrayList<>();
            if (vital.getSystolicBP() != null) {
                components.add(Map.of("code", loinc("8480-6", "Systolic blood pressure"),
                        "valueQuantity", quantity(vital.getSystolicBP(), "mmHg", "mm[Hg]")));
            }
            if (vital.getDiastolicBP() != null) {
                components.add(Map.of("code", loinc("8462-4", "Diastolic blood pressure"),
                        "valueQuantity", quantity(vital.getDiastolicBP(), "mmHg", "mm[Hg]")));
            }
            bp.put("component", components);
            out.put("bp", bp);
        }
        addVital(out, "sugar", vital, patientRef, "laboratory", "Laboratory",
                loinc("2339-0", "Glucose [Mass/volume] in Blood"), vital.getSugarLevel(), "mg/dL", "mg/dL");
        addVital(out, "weight", vital, patientRef, "vital-signs", "Vital Signs",
                loinc("29463-7", "Body weight"), vital.getWeight(), "kg", "kg");
        addVital(out, "temperature", vital, patientRef, "vital-signs", "Vital Signs",
                loinc("8310-5", "Body temperature"), vital.getTemperature(), "degF", "[degF]");
        addVital(out, "heart-rate", vital, patientRef, "vital-signs", "Vital Signs",
                loinc("8867-4", "Heart rate"), vital.getHeartRate(), "beats/minute", "/min");
        addVital(out, "respiratory-rate", vital, patientRef, "vital-signs", "Vital Signs",
                loinc("9279-1", "Respiratory rate"), vital.getRespiratoryRate(), "breaths/minute", "/min");
        return out;
    }

    private static Map<String, Object> allergy(String text, AppointmentIntake intake, String patientRef) {
        Map<String, Object> r = resource("AllergyIntolerance");
        r.put("clinicalStatus", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "code", "active"))));
        r.put("verificationStatus", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
                "code", "unconfirmed"))));
        r.put("code", Map.of("text", text));
        r.put("patient", reference(patientRef));
        if (intake.getCreatedAt() != null) {
            r.put("recordedDate", dateTime(intake.getCreatedAt()));
        }
        r.put("asserter", reference(patientRef));
        r.put("note", List.of(Map.of("text", "Reported by the patient on the pre-visit form.")));
        return r;
    }

    private static Map<String, Object> medicationStatement(String text, AppointmentIntake intake, String patientRef) {
        Map<String, Object> r = resource("MedicationStatement");
        r.put("status", "unknown");
        r.put("medicationCodeableConcept", Map.of("text", text));
        r.put("subject", reference(patientRef));
        if (intake.getCreatedAt() != null) {
            r.put("dateAsserted", dateTime(intake.getCreatedAt()));
        }
        r.put("informationSource", reference(patientRef));
        r.put("note", List.of(Map.of("text", "Reported by the patient on the pre-visit form.")));
        return r;
    }

    private static Map<String, Object> labReport(Document report, String patientRef) {
        Map<String, Object> r = resource("DiagnosticReport");
        r.put("status", "final");
        r.put("category", List.of(Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v2-0074", "code", "LAB", "display", "Laboratory")))));
        r.put("code", Map.of("text", labReportName(report)));
        r.put("subject", reference(patientRef));
        if (report.getUploadDate() != null) {
            r.put("effectiveDateTime", dateTime(report.getUploadDate()));
        }
        if (notBlank(report.getAiExplanation())) {
            r.put("conclusion", "AI explanation (not reviewed by a doctor): " + report.getAiExplanation().trim());
        }
        return r;
    }

    // ---- Helpers

    private static String labReportName(Document report) {
        if (report.getBooking() != null && report.getBooking().getSelectedTests() != null) {
            String tests = report.getBooking().getSelectedTests().stream()
                    .map(LabTest::getTestName)
                    .filter(FhirExportService::notBlank)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
            if (tests != null) {
                return tests;
            }
        }
        if (notBlank(report.getDescription())) {
            return report.getDescription().trim();
        }
        return notBlank(report.getOriginalFilename()) ? report.getOriginalFilename().trim() : "Lab report";
    }

    /**
     * The record's medicines with their doses, one pair per medicine. Same rules as the app's prescription table:
     * one medicine per line with the doses on matching lines; older records used ";" or "," between medicines.
     */
    static List<String[]> medicines(String medicine, String doses) {
        List<String> names = split(medicine, true);
        if (names.isEmpty()) {
            return List.of();
        }
        String doseText = doses == null ? "" : doses.replace("\r", "").trim();
        List<String> dosages = doseText.contains("\n")
                ? java.util.Arrays.stream(doseText.split("\n")).map(String::trim).toList()
                : split(doses, false);
        if (dosages.size() == 1 && names.size() > 1) {
            List<String> byComma = java.util.Arrays.stream(dosages.get(0).split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (byComma.size() == names.size()) {
                dosages = byComma;
            }
        }
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String dose = i < dosages.size() && notBlank(dosages.get(i)) ? dosages.get(i)
                    : dosages.size() == 1 ? dosages.get(0) : null;
            out.add(new String[] { names.get(i), dose });
        }
        return out;
    }

    /** A free-text list the patient typed ("Penicillin, peanuts"), without "none"-style answers */
    static List<String> items(String text) {
        return split(text, true).stream()
                .filter(item -> !NOTHING_REPORTED.matcher(item).matches())
                .toList();
    }

    private static List<String> split(String text, boolean allowComma) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        List<String> lines = clean(value.split("\\r?\\n"));
        if (lines.size() > 1) {
            return lines;
        }
        List<String> bySemicolon = clean(value.split(";"));
        if (bySemicolon.size() > 1) {
            return bySemicolon;
        }
        return allowComma ? clean(value.split(",")) : lines;
    }

    private static List<String> clean(String[] parts) {
        return java.util.Arrays.stream(parts)
                .map(part -> LIST_MARK.matcher(part).replaceFirst("").trim())
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static void addSection(List<Map<String, Object>> sections, String title, String text) {
        if (notBlank(text)) {
            sections.add(Map.of("title", title, "text", Map.of("status", "generated",
                    "div", "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + xhtml(text.trim()) + "</div>")));
        }
    }

    /** Escapes text for a FHIR narrative and keeps its line breaks */
    static String xhtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("\r", "").replace("\n", "<br/>");
    }

    private static void addVital(Map<String, Map<String, Object>> out, String key, Vital vital, String patientRef,
            String category, String categoryDisplay, Map<String, Object> code, Double value, String unit,
            String ucum) {
        if (value == null) {
            return;
        }
        Map<String, Object> r = observation(vital, patientRef, category, categoryDisplay, code);
        r.put("valueQuantity", quantity(value, unit, ucum));
        out.put(key, r);
    }

    private static Map<String, Object> observation(Vital vital, String patientRef, String category,
            String categoryDisplay, Map<String, Object> code) {
        Map<String, Object> r = resource("Observation");
        r.put("status", "final");
        r.put("category", List.of(category(category, categoryDisplay)));
        r.put("code", code);
        r.put("subject", reference(patientRef));
        if (vital.getRecordedAt() != null) {
            r.put("effectiveDateTime", dateTime(vital.getRecordedAt()));
        }
        return r;
    }

    private static Map<String, Object> category(String code, String display) {
        return Map.of("coding", List.of(Map.of("system", OBS_CATEGORY, "code", code, "display", display)));
    }

    private static Map<String, Object> loinc(String code, String display) {
        return Map.of("coding", List.of(Map.of("system", LOINC, "code", code, "display", display)), "text", display);
    }

    private static Map<String, Object> quantity(Double value, String unit, String ucum) {
        return Map.of("value", value, "unit", unit, "system", UCUM, "code", ucum);
    }

    private static Map<String, Object> resource(String type) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resourceType", type);
        return r;
    }

    private static Map<String, Object> entry(String fullUrl, Map<String, Object> resource) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("fullUrl", fullUrl);
        e.put("resource", resource);
        return e;
    }

    private static Map<String, Object> reference(String fullUrl) {
        return Map.of("reference", fullUrl);
    }

    /** A stable urn:uuid per resource, so the same record gets the same id in every export */
    static String ref(String type, Object id) {
        return "urn:uuid:" + UUID.nameUUIDFromBytes(("caresync:" + type + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static String gender(String gender) {
        if (gender == null) {
            return "unknown";
        }
        return switch (gender.trim().toLowerCase(Locale.ROOT)) {
            case "male", "m" -> "male";
            case "female", "f" -> "female";
            case "other" -> "other";
            default -> "unknown";
        };
    }

    /** App times are India time; FHIR dateTimes with a time need the offset */
    private static String dateTime(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }

    private static int compareDesc(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b == null ? 0 : 1;
        }
        return b == null ? -1 : b.compareTo(a);
    }

    private static String firstNotBlank(String first, String second) {
        return notBlank(first) ? first : second;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
