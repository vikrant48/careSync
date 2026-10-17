package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.SavedDoctorService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The logged-in patient's saved doctors. There is no patient id in the path: every call acts on the caller's own
 * list, so one patient cannot read or change another's.
 */
@RestController
@RequestMapping("/api/patients/me/saved-doctors")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasRole('PATIENT')")
public class SavedDoctorController {

    private final SavedDoctorService savedDoctorService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "My saved doctors", description = "Doctors the patient saved, newest first")
    @GetMapping
    public ResponseEntity<List<DoctorDto>> list() {
        return ResponseEntity.ok(savedDoctorService.list(currentPatientId()));
    }

    @Operation(summary = "Save a doctor", description = "Saving an already saved doctor does nothing")
    @PutMapping("/{doctorId}")
    public ResponseEntity<Void> save(@PathVariable Long doctorId) {
        savedDoctorService.save(currentPatientId(), doctorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove a saved doctor", description = "Removing a doctor that is not saved does nothing")
    @DeleteMapping("/{doctorId}")
    public ResponseEntity<Void> remove(@PathVariable Long doctorId) {
        savedDoctorService.remove(currentPatientId(), doctorId);
        return ResponseEntity.noContent().build();
    }

    private Long currentPatientId() {
        return currentUserService.requirePatient().getId();
    }
}
