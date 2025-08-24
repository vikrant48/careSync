package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.dto.UpdatePatientRequest;
import com.vikrant.careSync.dto.CreateMedicalHistoryRequest;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<Patient>> getAllPatients() {
        List<Patient> patients = patientService.getAllPatients();
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<Patient> getPatientById(@PathVariable Long id) {
        Optional<Patient> patient = patientService.getPatientById(id);
        return patient.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<Patient> getPatientByUsername(@PathVariable String username) {
        Optional<Patient> patient = patientService.getPatientByUsername(username);
        return patient.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profile/{username}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDto> getPatientProfile(@PathVariable String username) {
        try {
            Patient patient = patientService.getPatientProfile(username);
            PatientDto patientDto = new PatientDto(patient);
            return ResponseEntity.ok(patientDto);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Patient> updatePatientProfile(@PathVariable Long id, @RequestBody UpdatePatientRequest request) {
        try {
            Patient updatedPatient = new Patient();
            updatedPatient.setFirstName(request.getFirstName());
            updatedPatient.setLastName(request.getLastName());
            updatedPatient.setDateOfBirth(request.getDateOfBirth());
            updatedPatient.setContactInfo(request.getContactInfo());
            updatedPatient.setIllnessDetails(request.getIllnessDetails());
            updatedPatient.setEmail(request.getEmail());
            updatedPatient.setIsActive(request.getIsActive());
            
            Patient patient = patientService.updatePatientProfile(id, updatedPatient);
            return ResponseEntity.ok(patient);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/illness/{illnessKeyword}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<Patient>> getPatientsByIllness(@PathVariable String illnessKeyword) {
        List<Patient> patients = patientService.getPatientsByIllness(illnessKeyword);
        return ResponseEntity.ok(patients);
    }

    @PutMapping("/{id}/illness-details")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Patient> updateIllnessDetails(@PathVariable Long id, @RequestParam String illnessDetails) {
        try {
            Patient patient = patientService.updateIllnessDetails(id, illnessDetails);
            return ResponseEntity.ok(patient);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/contact-info")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Patient> updateContactInfo(@PathVariable Long id, @RequestParam String contactInfo) {
        try {
            Patient patient = patientService.updateContactInfo(id, contactInfo);
            return ResponseEntity.ok(patient);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Medical History Management
    @PostMapping("/{patientId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalHistory> addMedicalHistory(@PathVariable Long patientId, @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();
            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            
            MedicalHistory savedHistory = patientService.addMedicalHistory(patientId, medicalHistory);
            return ResponseEntity.ok(savedHistory);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{patientId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<List<MedicalHistory>> getPatientMedicalHistory(@PathVariable Long patientId) {
        List<MedicalHistory> medicalHistories = patientService.getPatientMedicalHistory(patientId);
        return ResponseEntity.ok(medicalHistories);
    }

    @GetMapping("/{patientId}/medical-history/date-range")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<List<MedicalHistory>> getMedicalHistoryByDateRange(
            @PathVariable Long patientId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            List<MedicalHistory> medicalHistories = patientService.getMedicalHistoryByDateRange(patientId, start, end);
            return ResponseEntity.ok(medicalHistories);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalHistory> updateMedicalHistory(@PathVariable Long historyId, @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();
            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            
            MedicalHistory updatedHistory = patientService.updateMedicalHistory(historyId, medicalHistory);
            return ResponseEntity.ok(updatedHistory);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteMedicalHistory(@PathVariable Long historyId) {
        try {
            patientService.deleteMedicalHistory(historyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}