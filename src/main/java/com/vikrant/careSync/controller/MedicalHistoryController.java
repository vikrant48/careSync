package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import org.springframework.security.access.prepost.PreAuthorize;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.dto.CreateMedicalHistoryRequest;
import com.vikrant.careSync.dto.MedicalHistoryDto;
import com.vikrant.careSync.dto.MedicalHistoryWithDoctorDto;
import com.vikrant.careSync.service.interfaces.IMedicalHistoryService;
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
    private final CurrentUserService currentUserService;


    @io.swagger.v3.oas.annotations.Operation(summary = "Create medical history", description = "Records a new medical visit entry for a patient (Doctor only)")
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR') and @access.canAccessPatient(#request.patientId)")
    public ResponseEntity<?> createMedicalHistory(@Valid @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();

            // Set patient from patientId
            Patient patient = new Patient();
            patient.setId(request.getPatientId());
            medicalHistory.setPatient(patient);

            // Set doctor from current authenticated user
            Doctor currentDoctor = currentUserService.requireDoctor();
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PostMapping("/with-doctor")
    @PreAuthorize("hasRole('DOCTOR') and @access.canAccessPatient(#request.patientId)")
    public ResponseEntity<?> createMedicalHistoryWithDoctor(
            @Valid @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();

            // Set patient from patientId
            Patient patient = new Patient();
            patient.setId(request.getPatientId());
            medicalHistory.setPatient(patient);

            // Set doctor from current authenticated user
            Doctor currentDoctor = currentUserService.requireDoctor();
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessMedicalHistory(#id)")
    public ResponseEntity<?> getMedicalHistoryById(@PathVariable Long id) {
        try {
            MedicalHistory medicalHistory = medicalHistoryService.getMedicalHistoryById(id);
            return ResponseEntity.ok(new MedicalHistoryDto(medicalHistory));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("@access.canAccessAppointment(#appointmentId)")
    public ResponseEntity<?> getMedicalHistoryByAppointment(@PathVariable Long appointmentId) {
        try {
            MedicalHistory medicalHistory = medicalHistoryService.getMedicalHistoryByAppointmentId(appointmentId);
            if (medicalHistory != null) {
                return ResponseEntity.ok(new MedicalHistoryDto(medicalHistory));
            }
            return ResponseEntity.ok(Map.of());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<?> getMedicalHistoryByPatient(@PathVariable Long patientId) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByPatient(patientId);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}/recent")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<?> getRecentMedicalHistory(@PathVariable Long patientId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<MedicalHistory> medicalHistories = medicalHistoryService.getRecentMedicalHistory(patientId, limit);
            List<MedicalHistoryDto> dtos = medicalHistories.stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}/by-date-range")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN') and @access.canAccessMedicalHistory(#id)")
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN') and @access.canAccessMedicalHistory(#id)")
    public ResponseEntity<?> deleteMedicalHistory(@PathVariable Long id) {
        try {
            medicalHistoryService.deleteMedicalHistory(id);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Medical history deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}/summary")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<?> getMedicalHistorySummary(@PathVariable Long patientId) {
        try {
            Map<String, Object> summary = medicalHistoryService.getMedicalHistorySummary(patientId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}/diagnosis/{diagnosis}")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}/with-doctor")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<?> getMedicalHistoryWithDoctorByPatient(@PathVariable Long patientId) {
        try {
            List<MedicalHistoryWithDoctorDto> medicalHistories = medicalHistoryService
                    .getMedicalHistoryWithDoctorByPatient(patientId);
            return ResponseEntity.ok(medicalHistories);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }
}