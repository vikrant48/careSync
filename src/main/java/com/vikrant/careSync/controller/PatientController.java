package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.entity.Document;

import com.vikrant.careSync.dto.UpdatePatientRequest;
import com.vikrant.careSync.dto.CreateMedicalHistoryRequest;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.service.interfaces.IPatientService;
import com.vikrant.careSync.service.interfaces.IMedicalHistoryService;
import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class PatientController {

    private final IPatientService patientService;
    private final IMedicalHistoryService medicalHistoryService;
    private final DocumentService documentService;
    private final UserService userService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

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
            Patient patient = patientService.getPatientByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found with username: " + username));
            PatientDto patientDto = new PatientDto(patient);
            return ResponseEntity.ok(patientDto);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{patientId}/complete-data")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getCompletePatientData(@PathVariable Long patientId) {
        try {
            Optional<Patient> patientOpt = patientService.getPatientById(patientId);
            if (patientOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Patient patient = patientOpt.get();
            Map<String, Object> completeData = new HashMap<>();

            // Patient basic information
            PatientDto patientDto = new PatientDto(patient);
            completeData.put("patient", patientDto);

            // Medical history
            List<MedicalHistory> medicalHistory = patientService.getPatientMedicalHistory(patientId);
            completeData.put("medicalHistory", medicalHistory);

            // Documents
            List<Document> documents = documentService.getDocumentsByPatientId(patientId);
            completeData.put("documents", documents);

            return ResponseEntity.ok(completeData);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDto> updatePatientProfile(@PathVariable Long id,
            @RequestBody UpdatePatientRequest request) {
        try {
            Patient updatedPatient = new Patient();
            updatedPatient.setFirstName(request.getFirstName());
            updatedPatient.setLastName(request.getLastName());
            updatedPatient.setDateOfBirth(request.getDateOfBirth());
            updatedPatient.setContactInfo(request.getContactInfo());
            updatedPatient.setIllnessDetails(request.getIllnessDetails());
            updatedPatient.setEmail(request.getEmail());
            updatedPatient.setIsActive(request.getIsActive());
            updatedPatient.setGender(request.getGender());

            Patient patient = patientService.updatePatientProfile(id, updatedPatient);
            return ResponseEntity.ok(new PatientDto(patient));
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
    public ResponseEntity<PatientDto> updateIllnessDetails(@PathVariable Long id, @RequestParam String illnessDetails) {
        try {
            Patient patient = patientService.getPatientById(id)
                    .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
            patient.setIllnessDetails(illnessDetails);
            Patient updatedPatient = patientService.updatePatientProfile(id, patient);
            return ResponseEntity.ok(new PatientDto(updatedPatient));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/contact-info")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDto> updateContactInfo(@PathVariable Long id, @RequestParam String contactInfo) {
        try {
            Patient patient = patientService.getPatientById(id)
                    .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
            patient.setContactInfo(contactInfo);
            Patient updatedPatient = patientService.updatePatientProfile(id, patient);
            return ResponseEntity.ok(new PatientDto(updatedPatient));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/profile-image")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDto> updateProfileImage(@PathVariable Long id, @RequestParam String imageUrl) {
        try {
            Patient patient = patientService.updateProfileImage(id, imageUrl);
            return ResponseEntity.ok(new PatientDto(patient));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/profile/{username}/image")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDto> updateProfileImageByUsername(@PathVariable String username,
            @RequestParam String imageUrl) {
        try {
            Patient patient = patientService.updateProfileImageByUsername(username, imageUrl);
            return ResponseEntity.ok(new PatientDto(patient));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Medical History Management
    @PostMapping("/{patientId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalHistory> addMedicalHistory(@PathVariable Long patientId,
            @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();
            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            medicalHistory.setMedicine(request.getMedicine());
            medicalHistory.setDoses(request.getDoses());
            medicalHistory.setNotes(request.getNotes());

            MedicalHistory savedHistory = patientService.addMedicalHistory(patientId, medicalHistory);
            return ResponseEntity.ok(savedHistory);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{patientId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<List<MedicalHistory>> getPatientMedicalHistory(@PathVariable Long patientId) {
        List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByPatient(patientId);
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
            List<MedicalHistory> medicalHistories = medicalHistoryService.getMedicalHistoryByDateRange(patientId, start,
                    end);
            return ResponseEntity.ok(medicalHistories);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalHistory> updateMedicalHistory(@PathVariable Long historyId,
            @RequestBody CreateMedicalHistoryRequest request) {
        try {
            MedicalHistory medicalHistory = new MedicalHistory();
            medicalHistory.setVisitDate(request.getVisitDate());
            medicalHistory.setSymptoms(request.getSymptoms());
            medicalHistory.setDiagnosis(request.getDiagnosis());
            medicalHistory.setTreatment(request.getTreatment());
            medicalHistory.setMedicine(request.getMedicine());
            medicalHistory.setDoses(request.getDoses());
            medicalHistory.setNotes(request.getNotes());

            MedicalHistory updatedHistory = medicalHistoryService.updateMedicalHistory(historyId, medicalHistory);
            return ResponseEntity.ok(updatedHistory);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteMedicalHistory(@PathVariable Long historyId) {
        try {
            medicalHistoryService.deleteMedicalHistory(historyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // File Upload Endpoints for Patients

    /**
     * Upload profile image for patient
     */
    @PostMapping("/{patientId}/upload/profile-image")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadProfileImage(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, Document.DocumentType.PROFILE_IMAGE, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload profile image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Upload medical document for patient
     */
    @PostMapping("/{patientId}/upload/medical-document")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadMedicalDocument(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "MEDICAL_DOCUMENT") String documentType,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, type, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            response.put("documentType", document.getDocumentType());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload medical document: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file or document type: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Upload prescription for patient
     */
    @PostMapping("/{patientId}/upload/prescription")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadPrescription(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, Document.DocumentType.PRESCRIPTION, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload prescription: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Upload lab report for patient
     */
    @PostMapping("/{patientId}/upload/lab-report")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadLabReport(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, Document.DocumentType.LAB_REPORT, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload lab report: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Upload insurance document for patient
     */
    @PostMapping("/{patientId}/upload/insurance")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadInsuranceDocument(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, Document.DocumentType.INSURANCE_DOCUMENT, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload insurance document: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Upload identification document for patient
     */
    @PostMapping("/{patientId}/upload/identification")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadIdentificationDocument(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";

            Document document = documentService.uploadDocumentForPatient(
                    file, patientId, Document.DocumentType.IDENTIFICATION, description, username, userType);

            Map<String, Object> response = createDocumentResponse(document);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload identification document: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get all documents for a patient
     */
    @GetMapping("/{patientId}/documents")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getPatientDocuments(@PathVariable Long patientId) {
        try {
            List<Document> documents = documentService.getDocumentsByPatientId(patientId);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get documents by type for a patient
     */
    @GetMapping("/{patientId}/documents/type/{documentType}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getPatientDocumentsByType(
            @PathVariable Long patientId,
            @PathVariable String documentType) {
        try {
            Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
            List<Document> documents = documentService.getDocumentsByPatientIdAndType(patientId, type);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get patient profile image
     */
    @GetMapping("/{patientId}/profile-image")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getPatientProfileImage(@PathVariable Long patientId) {
        try {
            List<Document> documents = documentService.getDocumentsByPatientIdAndType(
                    patientId, Document.DocumentType.PROFILE_IMAGE);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Helper method to create document response
     */
    private Map<String, Object> createDocumentResponse(Document document) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", document.getId());
        response.put("filename", document.getOriginalFilename());
        response.put("url", documentService.getFileUrl(document.getId()));
        response.put("downloadUrl", documentService.getDownloadUrl(document.getId()));
        response.put("cloudinaryUrl", document.getFileUrl()); // Direct Cloudinary URL
        response.put("size", document.getFileSize());
        response.put("uploadDate", document.getUploadDate());
        response.put("description", document.getDescription());
        response.put("contentType", document.getContentType());
        return response;
    }
}