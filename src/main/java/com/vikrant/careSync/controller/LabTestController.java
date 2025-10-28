package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.CreateLabTestRequest;
import com.vikrant.careSync.dto.LabTestDto;
import com.vikrant.careSync.service.LabTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/lab-tests")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LabTestController {

    private final LabTestService labTestService;

    /**
     * Get all available lab tests
     * Accessible by all authenticated users
     */
    @GetMapping
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT') or hasRole('ADMIN')")
    public ResponseEntity<List<LabTestDto>> getAllLabTests() {
        try {
            List<LabTestDto> labTests = labTestService.getAllActiveLabTests();
            return ResponseEntity.ok(labTests);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all lab tests including inactive ones (Admin and Doctor)
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<List<LabTestDto>> getAllLabTestsIncludingInactive() {
        try {
            List<LabTestDto> labTests = labTestService.getAllLabTests();
            return ResponseEntity.ok(labTests);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get lab test by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT') or hasRole('ADMIN')")
    public ResponseEntity<LabTestDto> getLabTestById(@PathVariable Long id) {
        try {
            Optional<LabTestDto> labTest = labTestService.getLabTestById(id);
            return labTest.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a new lab test
     * Only accessible by Admin and Doctor
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<?> createLabTest(@Valid @RequestBody CreateLabTestRequest request) {
        try {
            LabTestDto createdLabTest = labTestService.createLabTest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdLabTest);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while creating the lab test");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update an existing lab test
     * Only accessible by Admin and Doctor
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<?> updateLabTest(@PathVariable Long id, @Valid @RequestBody CreateLabTestRequest request) {
        try {
            LabTestDto updatedLabTest = labTestService.updateLabTest(id, request);
            return ResponseEntity.ok(updatedLabTest);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while updating the lab test");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Soft delete a lab test (set as inactive)
     * Only accessible by Admin
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteLabTest(@PathVariable Long id) {
        try {
            labTestService.deleteLabTest(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Lab test deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while deleting the lab test");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Search lab tests by name
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT') or hasRole('ADMIN')")
    public ResponseEntity<List<LabTestDto>> searchLabTestsByName(@RequestParam String testName) {
        try {
            List<LabTestDto> labTests = labTestService.searchLabTestsByName(testName);
            return ResponseEntity.ok(labTests);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get total price for selected tests
     */
    @PostMapping("/calculate-price")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT') or hasRole('ADMIN')")
    public ResponseEntity<?> calculateTotalPrice(@RequestBody List<Long> testIds) {
        try {
            if (testIds == null || testIds.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Test IDs are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            java.math.BigDecimal totalPrice = labTestService.calculateTotalPrice(testIds);
            Map<String, Object> response = new HashMap<>();
            response.put("totalPrice", totalPrice);
            response.put("testCount", testIds.size());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An error occurred while calculating the total price");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}