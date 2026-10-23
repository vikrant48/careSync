package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.AppointmentIntake;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Vital;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The FHIR export builds a valid-shaped R4 Bundle from the patient's signed record only. */
class FhirExportServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 10, 0);

    private static Patient patient() {
        Patient p = new Patient();
        p.setId(7L);
        p.setFirstName("Test");
        p.setLastName("Patient");
        p.setGender("FEMALE");
        p.setDateOfBirth(LocalDate.of(1990, 1, 2));
        p.setBloodGroup("O+");
        return p;
    }

    private static Doctor doctor() {
        Doctor d = new Doctor();
        d.setId(3L);
        d.setFirstName("Asha");
        d.setLastName("Rao");
        d.setSpecialization("General Physician");
        return d;
    }

    private static MedicalHistory record(Long id) {
        MedicalHistory mh = new MedicalHistory();
        mh.setId(id);
        mh.setDoctor(doctor());
        mh.setVisitDate(LocalDate.of(2026, 10, 1));
        mh.setDiagnosis("Viral fever");
        mh.setSubjective("Fever for 3 days <high>");
        mh.setMedicine("Paracetamol 500mg\nCetirizine 10mg");
        mh.setDoses("1 tablet, twice daily\n1 tablet at night");
        mh.setIsSigned(true);
        mh.setIsDraft(false);
        return mh;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resources(Map<String, Object> bundle) {
        return ((List<Map<String, Object>>) bundle.get("entry")).stream()
                .map(e -> (Map<String, Object>) e.get("resource"))
                .toList();
    }

    private static long count(List<Map<String, Object>> resources, String type) {
        return resources.stream().filter(r -> type.equals(r.get("resourceType"))).count();
    }

    @Test
    void buildsCollectionBundleWithVisitResources() {
        Map<String, Object> bundle = FhirExportService.bundle(patient(), List.of(record(11L)), List.of(), List.of(),
                List.of(), NOW);

        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("collection", bundle.get("type"));
        List<Map<String, Object>> r = resources(bundle);
        assertEquals(1, count(r, "Patient"));
        assertEquals(1, count(r, "Practitioner"));
        assertEquals(1, count(r, "Encounter"));
        assertEquals(1, count(r, "Condition"));
        assertEquals(1, count(r, "Composition"));
        assertEquals(2, count(r, "MedicationRequest"));
        // blood group
        assertEquals(1, count(r, "Observation"));

        Map<String, Object> patient = r.get(0);
        assertEquals("female", patient.get("gender"));
        assertEquals("1990-01-02", patient.get("birthDate"));
    }

    @Test
    void sameDoctorIsListedOnce() {
        Map<String, Object> bundle = FhirExportService.bundle(patient(), List.of(record(11L), record(12L)), List.of(),
                List.of(), List.of(), NOW);
        assertEquals(1, count(resources(bundle), "Practitioner"));
        assertEquals(2, count(resources(bundle), "Encounter"));
    }

    @Test
    void vitalsBecomeOneObservationPerValue() {
        Vital v = new Vital();
        v.setId(5L);
        v.setSystolicBP(120.0);
        v.setDiastolicBP(80.0);
        v.setTemperature(98.6);
        v.setRecordedAt(NOW);
        Patient p = patient();
        p.setBloodGroup(null);

        List<Map<String, Object>> r = resources(FhirExportService.bundle(p, List.of(), List.of(v), List.of(), List.of(), NOW));
        // BP panel + temperature
        assertEquals(2, count(r, "Observation"));
    }

    @Test
    void intakeAllergiesSkipNoneAndDuplicates() {
        AppointmentIntake first = new AppointmentIntake();
        first.setAllergies("Penicillin, peanuts");
        first.setCurrentMedications("none");
        first.setCreatedAt(NOW.minusDays(10));
        AppointmentIntake second = new AppointmentIntake();
        second.setAllergies("penicillin");
        second.setCurrentMedications("Metformin 500mg");
        second.setCreatedAt(NOW.minusDays(1));

        List<Map<String, Object>> r = resources(FhirExportService.bundle(patient(), List.of(), List.of(),
                List.of(first, second), List.of(), NOW));
        assertEquals(2, count(r, "AllergyIntolerance"));
        assertEquals(1, count(r, "MedicationStatement"));
    }

    @Test
    void labReportAiTextIsLabelledAndInactiveReportsSkipped() {
        Document active = new Document();
        active.setId(1L);
        active.setDescription("CBC");
        active.setAiExplanation("Haemoglobin is in range.");
        active.setIsActive(true);
        Document removed = new Document();
        removed.setId(2L);
        removed.setIsActive(false);

        List<Map<String, Object>> r = resources(FhirExportService.bundle(patient(), List.of(), List.of(), List.of(),
                List.of(active, removed), NOW));
        assertEquals(1, count(r, "DiagnosticReport"));
        Map<String, Object> report = r.stream().filter(x -> "DiagnosticReport".equals(x.get("resourceType")))
                .findFirst().orElseThrow();
        assertTrue(String.valueOf(report.get("conclusion")).startsWith("AI explanation (not reviewed by a doctor)"));
    }

    @Test
    void medicinesPairDosesByLine() {
        List<String[]> meds = FhirExportService.medicines("Paracetamol 500mg\nCetirizine 10mg",
                "1 tablet, twice daily\n1 tablet at night");
        assertEquals(2, meds.size());
        assertEquals("1 tablet, twice daily", meds.get(0)[1]);
        assertEquals("1 tablet at night", meds.get(1)[1]);
    }

    @Test
    void narrativeTextIsEscaped() {
        assertEquals("a &lt;b&gt; &amp; c<br/>d", FhirExportService.xhtml("a <b> & c\nd"));
    }

    @Test
    void referencesAreStablePerRecord() {
        assertEquals(FhirExportService.ref("Encounter", 11L), FhirExportService.ref("Encounter", 11L));
        assertNotEquals(FhirExportService.ref("Encounter", 11L), FhirExportService.ref("Encounter", 12L));
    }
}
