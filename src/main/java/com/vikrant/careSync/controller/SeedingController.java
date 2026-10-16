package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.dto.SeedBulkResponse;
import com.vikrant.careSync.dto.SeedUserRequest;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.service.interfaces.ISeedingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Disabled unless app.seeding.enabled=true; even then ADMIN-only and cannot create ADMIN accounts.
@RestController
@RequestMapping("/api/seed")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Seeding API", description = "Admin-only endpoints for seeding Patient and Doctor users in bulk or individually")
public class SeedingController {

    private final ISeedingService seedingService;

    @Operation(summary = "Seed single user", description = "Creates a single user (PATIENT or DOCTOR) in users and role-specific tables with automatic field encryption")
    @PostMapping({ "/public/seed-single", "/patients/public/seed-single" })
    public ResponseEntity<?> seedSingleUser(@RequestBody(required = false) SeedUserRequest request) {
        try {
            Object result = seedingService.seedSingleUser(request);
            if (result instanceof Patient p) {
                return ResponseEntity.ok(new PatientDto(p));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @Operation(summary = "Seed bulk users", description = "Creates multiple users (PATIENT or DOCTOR) in bulk from a JSON array payload")
    @PostMapping({ "/public/seed-bulk", "/patients/public/seed-bulk" })
    public ResponseEntity<?> seedBulkUsers(
            @RequestBody List<SeedUserRequest> requests,
            @RequestParam(required = false, defaultValue = "demo") String password) {
        try {
            SeedBulkResponse response = seedingService.seedBulkUsers(requests, password);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }
}
