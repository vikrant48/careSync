package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.VitalDto;
import com.vikrant.careSync.service.VitalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vitals")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@Tag(name = "Vitals", description = "Endpoints for tracking patient vitals like Blood Pressure, Sugar, and Weight")
@SecurityRequirement(name = "bearerAuth")
public class VitalController {

    private final VitalService vitalService;

    @Operation(summary = "Log new vital", description = "Records new health metrics for a patient")
    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<VitalDto> logVital(@RequestBody VitalDto vitalDto) {
        return ResponseEntity.ok(vitalService.logVital(vitalDto));
    }

    @Operation(summary = "Get patient vitals", description = "Retrieves all recorded vitals for a specific patient")
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<List<VitalDto>> getPatientVitals(@PathVariable Long patientId) {
        return ResponseEntity.ok(vitalService.getPatientVitals(patientId));
    }
}
