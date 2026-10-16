package com.vikrant.careSync.controller;

import com.vikrant.careSync.service.storage.FileContentValidator;
import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.Certificate;

import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.service.DoctorService;
import com.vikrant.careSync.service.PatientService;
import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.dto.DocumentDto;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@io.swagger.v3.oas.annotations.tags.Tag(name = "File Uploads", description = "Endpoints for uploading certificates, profile images, medical documents, and lab reports")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class FileUploadController {

    private final DocumentService documentService;
    private final com.vikrant.careSync.controller.support.DocumentUploads documentUploads;
    private final UserService userService;
    private final DoctorService doctorService;
    private final PatientService patientService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AccessGuard accessGuard;
    private final CurrentUserService currentUserService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Upload certificate", description = "Uploads a professional certificate for a doctor")
    @PostMapping(value = "/upload/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId) and (#certificateId == null or @access.ownsCertificate(#certificateId))")
    public ResponseEntity<?> uploadCertificate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "certificateId", required = false) Long certificateId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "certificate", "Invalid file",
                (username, userType) -> {
                    Document document = documentService.uploadDocumentForDoctor(
                            file, doctorId, Document.DocumentType.CERTIFICATE, description, username, userType);

                    String fileUrl = document.getFilePath();
                    Certificate savedCertificate;

                    if (certificateId != null) {
                        // Update existing certificate with new file URL
                        savedCertificate = doctorService.updateCertificateUrl(certificateId, fileUrl);
                    } else {
                        // Create a new certificate record with Supabase URL
                        Certificate certificate = new Certificate();
                        certificate.setUrl(fileUrl);
                        certificate.setName(document.getOriginalFilename());
                        certificate.setDetails(description);

                        savedCertificate = doctorService.addCertificate(doctorId, certificate);
                    }

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    response.put("certificateId", savedCertificate.getId());
                    response.put("message", certificateId != null ? "Certificate file updated successfully"
                            : "Certificate created successfully");
                    return response;
                });
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Upload doctor profile image", description = "Uploads and updates a doctor's profile photo")
    @PostMapping(value = "/upload/profile-image/doctor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<?> uploadDoctorProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "profile image", "Invalid file",
                (username, userType) -> {
                    Document document = documentService.uploadDocumentForDoctor(
                            file, doctorId, Document.DocumentType.PROFILE_IMAGE, description, username, userType);

                    // Automatically update doctor's profile image URL
                    String fileUrl = document.getFilePath();
                    doctorService.updateProfileImage(doctorId, fileUrl);

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    return response;
                });
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Upload patient profile image", description = "Uploads and updates a patient's profile photo")
    @PostMapping(value = "/upload/profile-image/patient", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PATIENT') and @access.isSelf(#patientId)")
    public ResponseEntity<?> uploadPatientProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") Long patientId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "profile image", "Invalid file",
                (username, userType) -> {
                    Document document = documentService.uploadDocumentForPatient(
                            file, patientId, Document.DocumentType.PROFILE_IMAGE, description, username, userType);

                    // Automatically update patient's profile image URL
                    String fileUrl = document.getFilePath();
                    patientService.updateProfileImage(patientId, fileUrl);

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    return response;
                });
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Upload medical document", description = "Uploads a generic medical document for a patient (Doctor only)")
    @PostMapping(value = "/upload/medical-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR') and @access.canAccessPatient(#patientId)")
    public ResponseEntity<?> uploadMedicalDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") Long patientId,
            @RequestParam(value = "documentType", defaultValue = "MEDICAL_DOCUMENT") String documentType,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "medical document", "Invalid file or document type",
                (username, userType) -> {
                    Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
                    Document document = documentService.uploadDocumentForPatient(
                            file, patientId, type, description, username, userType);

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    response.put("documentType", document.getDocumentType());
                    return response;
                });
    }

    @PostMapping("/upload/doctor-document")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<?> uploadDoctorDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "documentType", defaultValue = "OTHER") String documentType,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "document", "Invalid file or document type",
                (username, userType) -> {
                    Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
                    Document document = documentService.uploadDocumentForDoctor(
                            file, doctorId, type, description, username, userType);

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    response.put("documentType", document.getDocumentType());
                    return response;
                });
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Upload lab report", description = "Uploads a laboratory report linked to a specific booking")
    @PostMapping(value = "/upload/lab-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR') and @access.canAccessPatient(#patientId)")
    public ResponseEntity<?> uploadLabReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("patientId") Long patientId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        return documentUploads.handle(authentication, "lab report", "Invalid file or data",
                (username, userType) -> {
                    Document document = documentService.uploadLabReport(
                            file, bookingId, patientId, description, username, userType);

                    Map<String, Object> response = documentUploads.standardResponse(document);
                    response.put("documentType", document.getDocumentType());
                    return response;
                });
    }

    @GetMapping("/download/{documentId}")
    @PreAuthorize("@access.canAccessDocument(#documentId)")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long documentId) {
        try {
            Document document = documentService.getDocumentById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            Resource resource = documentService.getFileAsResource(documentId);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + FileContentValidator.safeFilename(document.getOriginalFilename()) + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(document.getContentType()))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/view/{documentId}")
    @PreAuthorize("@access.canAccessDocument(#documentId)")
    public ResponseEntity<Resource> viewFile(@PathVariable Long documentId) {
        try {
            Document document = documentService.getDocumentById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            Resource resource = documentService.getFileAsResource(documentId);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getContentType()))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessDocument(#documentId)")
    public ResponseEntity<?> deleteFile(@PathVariable Long documentId) {
        try {
            documentService.deleteDocument(documentId);
            return ResponseEntity.ok().body("Document deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to delete document: " + ClientErrorMessages.of(e));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ClientErrorMessages.of(e));
        }
    }

    // Get documents by doctor ID
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<List<DocumentDto>> getDoctorDocuments(@PathVariable Long doctorId) {
        try {
            List<DocumentDto> documents = documentService.getDocumentsByDoctorId(doctorId).stream()
                    .map(doc -> documentService.toDto(doc))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<List<DocumentDto>> getPatientDocuments(@PathVariable Long patientId) {
        try {
            return ResponseEntity.ok(documentService.withFreshUrls(documentService.getDocumentsDtoByPatientId(patientId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get documents by type
    @GetMapping("/doctor/{doctorId}/type/{documentType}")
    @PreAuthorize("hasRole('DOCTOR') and @access.isSelf(#doctorId)")
    public ResponseEntity<List<DocumentDto>> getDoctorDocumentsByType(
            @PathVariable Long doctorId,
            @PathVariable String documentType) {
        try {
            Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
            List<DocumentDto> documents = documentService.getDocumentsByDoctorIdAndType(doctorId, type).stream()
                    .map(doc -> documentService.toDto(doc))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/patient/{patientId}/type/{documentType}")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
    public ResponseEntity<List<DocumentDto>> getPatientDocumentsByType(
            @PathVariable Long patientId,
            @PathVariable String documentType) {
        try {
            Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
            List<DocumentDto> documents = documentService.getDocumentsByPatientIdAndType(patientId, type).stream()
                    .map(doc -> documentService.toDto(doc))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update document description
    @PutMapping("/{documentId}/description")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT') and @access.canAccessDocument(#documentId)")
    public ResponseEntity<?> updateDocumentDescription(
            @PathVariable Long documentId,
            @RequestParam String description) {
        try {
            Document document = documentService.updateDocumentDescription(documentId, description);
            DocumentDto dto = documentService.toDto(document);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ClientErrorMessages.of(e));
        }
    }

    // Get recent documents
    @GetMapping("/recent")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<DocumentDto>> getRecentDocuments(
            @RequestParam(defaultValue = "7") int days) {
        try {
            // Only documents of the doctor's own patients, or documents the doctor owns
            Long doctorId = currentUserService.require().id();
            java.util.Set<Long> patientIds = new java.util.HashSet<>(accessGuard.treatedPatientIds());
            List<DocumentDto> documents = documentService.getRecentDocuments(days).stream()
                    .filter(doc -> (doc.getPatient() != null && patientIds.contains(doc.getPatient().getId()))
                            || (doc.getPatient() == null && doc.getDoctor() != null
                                    && doctorId.equals(doc.getDoctor().getId())))
                    .map(doc -> documentService.toDto(doc))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}