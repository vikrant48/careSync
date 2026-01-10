package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.dto.CreateMedicalHistoryRequest;
import com.vikrant.careSync.dto.MedicalHistoryDto;
import com.vikrant.careSync.dto.MedicalHistoryWithDoctorDto;
import com.vikrant.careSync.service.interfaces.IMedicalHistoryService;
import com.vikrant.careSync.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/medical-history")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Medical History", description = "Endpoints for recording and retrieving patient medical history and prescriptions")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class MedicalHistoryController {

    private final IMedicalHistoryService medicalHistoryService;
    private final DoctorRepository doctorRepository;

    // Get current authenticated doctor
    private Doctor getCurrentDoctor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return doctorRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Doctor not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create medical history", description = "Records a new medical visit entry for a patient (Doctor only)")
    @PostMapping
    public ResponseEntity<?> createMedicalHistory(@Valid @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();

            // Set patient from patientId
            Patient patient = new Patient();
            patient.setId(request.getPatientId());
            medicalHistory.setPatient(patient);

            // Set doctor from current authenticated user
            Doctor currentDoctor = getCurrentDoctor();
            medicalHistory.setDoctor(currentDoctor);

            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            medicalHistory.setMedicine(request.getMedicine());
            medicalHistory.setDoses(request.getDoses());
            medicalHistory.setNotes(request.getNotes());
            medicalHistory.setAppointmentId(request.getAppointmentId());

            MedicalHistory createdHistory = medicalHistoryService.createMedicalHistory(medicalHistory);
            return ResponseEntity.ok(new MedicalHistoryDto(createdHistory, "Medical history created successfully"));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/with-doctor")
    public ResponseEntity<?> createMedicalHistoryWithDoctor(
            @Valid @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();

            // Set patient from patientId
            Patient patient = new Patient();
            patient.setId(request.getPatientId());
            medicalHistory.setPatient(patient);

            // Set doctor from current authenticated user
            Doctor currentDoctor = getCurrentDoctor();
            medicalHistory.setDoctor(currentDoctor);

            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            medicalHistory.setMedicine(request.getMedicine());
            medicalHistory.setDoses(request.getDoses());
            medicalHistory.setNotes(request.getNotes());
            medicalHistory.setAppointmentId(request.getAppointmentId());

            MedicalHistory createdHistory = medicalHistoryService.createMedicalHistoryWithDoctor(medicalHistory,
                    currentDoctor.getId());
            return ResponseEntity.ok(new MedicalHistoryDto(createdHistory, "Medical history created successfully"));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMedicalHistoryById(@PathVariable Long id) {
        try {
            MedicalHistory medicalHistory = medicalHistoryService.getMedicalHistoryById(id);
            return ResponseEntity.ok(new MedicalHistoryDto(medicalHistory));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getMedicalHistoryByPatient(@PathVariable Long patientId) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByPatient(patientId);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}/recent")
    public ResponseEntity<?> getRecentMedicalHistory(@PathVariable Long patientId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getRecentMedicalHistory(patientId, limit);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}/by-date-range")
    public ResponseEntity<?> getMedicalHistoryByDateRange(
            @PathVariable Long patientId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByDateRange(patientId,
                    startDate, endDate);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMedicalHistory(@PathVariable Long id,
            @Valid @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();
            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            medicalHistory.setMedicine(request.getMedicine());
            medicalHistory.setDoses(request.getDoses());
            medicalHistory.setNotes(request.getNotes());
            medicalHistory.setAppointmentId(request.getAppointmentId());

            MedicalHistory updatedHistory = medicalHistoryService.updateMedicalHistory(id, medicalHistory);
            return ResponseEntity.ok(new MedicalHistoryDto(updatedHistory));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMedicalHistory(@PathVariable Long id) {
        try {
            medicalHistoryService.deleteMedicalHistory(id);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Medical history deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}/summary")
    public ResponseEntity<?> getMedicalHistorySummary(@PathVariable Long patientId) {
        try {
            Map<String, Object> summary = medicalHistoryService.getMedicalHistorySummary(patientId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}/diagnosis/{diagnosis}")
    public ResponseEntity<?> getMedicalHistoryByDiagnosis(
            @PathVariable Long patientId,
            @PathVariable String diagnosis) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByDiagnosis(patientId,
                    diagnosis);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}/with-doctor")
    public ResponseEntity<?> getMedicalHistoryWithDoctorByPatient(@PathVariable Long patientId) {
        try {
            List<MedicalHistoryWithDoctorDto> medicalHistories = medicalHistoryService
                    .getMedicalHistoryWithDoctorByPatient(patientId);
            return ResponseEntity.ok(medicalHistories);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}