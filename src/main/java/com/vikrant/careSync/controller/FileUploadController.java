package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.Certificate;

import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.UserService;
import com.vikrant.careSync.service.DoctorService;
import com.vikrant.careSync.service.PatientService;
import com.vikrant.careSync.dto.UserDto;
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

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final DocumentService documentService;
    private final UserService userService;
    private final DoctorService doctorService;
    private final PatientService patientService;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @PostMapping("/upload/certificate")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> uploadCertificate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "certificateId", required = false) Long certificateId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";
            
            Document document = documentService.uploadDocumentForDoctor(
                file, doctorId, Document.DocumentType.CERTIFICATE, description, username, userType);
                
            String cloudinaryUrl = document.getFileUrl();
            Certificate savedCertificate;
            
            if (certificateId != null) {
                // Update existing certificate with new file URL
                savedCertificate = doctorService.updateCertificateUrl(certificateId, cloudinaryUrl);
            } else {
                // Create a new certificate record with Cloudinary URL
                Certificate certificate = new Certificate();
                certificate.setUrl(cloudinaryUrl);
                certificate.setName(document.getOriginalFilename());
                certificate.setDetails(description);
                
                savedCertificate = doctorService.addCertificate(doctorId, certificate);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("filename", document.getOriginalFilename());
            response.put("url", documentService.getFileUrl(document.getId()));
            response.put("downloadUrl", documentService.getDownloadUrl(document.getId()));
            response.put("cloudinaryUrl", cloudinaryUrl); // Direct Cloudinary URL
            response.put("size", document.getFileSize());
            response.put("uploadDate", document.getUploadDate());
            response.put("certificateId", savedCertificate.getId());
            response.put("message", certificateId != null ? "Certificate file updated successfully" : "Certificate created successfully");
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload certificate: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/upload/profile-image/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> uploadDoctorProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";
            
            Document document = documentService.uploadDocumentForDoctor(
                file, doctorId, Document.DocumentType.PROFILE_IMAGE, description, username, userType);
            
            // Automatically update doctor's profile image URL
            String cloudinaryUrl = document.getFileUrl();
            doctorService.updateProfileImage(doctorId, cloudinaryUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("filename", document.getOriginalFilename());
            response.put("url", documentService.getFileUrl(document.getId()));
            response.put("downloadUrl", documentService.getDownloadUrl(document.getId()));
            response.put("cloudinaryUrl", cloudinaryUrl); // Direct Cloudinary URL
            response.put("size", document.getFileSize());
            response.put("uploadDate", document.getUploadDate());
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload profile image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    @PostMapping("/upload/profile-image/patient")
    @PreAuthorize("hasRole('PATIENT') or hasRole('DOCTOR')")
    public ResponseEntity<?> uploadPatientProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") Long patientId,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String username = authentication.getName();
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";
            
            Document document = documentService.uploadDocumentForPatient(
                file, patientId, Document.DocumentType.PROFILE_IMAGE, description, username, userType);
            
            // Automatically update patient's profile image URL
            String cloudinaryUrl = document.getFileUrl();
            patientService.updateProfileImage(patientId, cloudinaryUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("filename", document.getOriginalFilename());
            response.put("url", documentService.getFileUrl(document.getId()));
            response.put("downloadUrl", documentService.getDownloadUrl(document.getId()));
            response.put("cloudinaryUrl", cloudinaryUrl); // Direct Cloudinary URL
            response.put("size", document.getFileSize());
            response.put("uploadDate", document.getUploadDate());
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload profile image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/upload/medical-document")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> uploadMedicalDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") Long patientId,
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("filename", document.getOriginalFilename());
            response.put("url", documentService.getFileUrl(document.getId()));
            response.put("downloadUrl", documentService.getDownloadUrl(document.getId()));
            response.put("cloudinaryUrl", document.getFileUrl()); // Direct Cloudinary URL
            response.put("size", document.getFileSize());
            response.put("uploadDate", document.getUploadDate());
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

    @GetMapping("/download/{documentId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long documentId) {
        try {
            Document document = documentService.getDocumentById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            
            Resource resource = documentService.getFileAsResource(documentId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + document.getOriginalFilename() + "\"");
            
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
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<?> deleteFile(@PathVariable Long documentId) {
        try {
            documentService.deleteDocument(documentId);
            return ResponseEntity.ok().body("Document deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to delete document: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Get documents by doctor ID
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getDoctorDocuments(@PathVariable Long doctorId) {
        try {
            List<Document> documents = documentService.getDocumentsByDoctorId(doctorId);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Get documents by patient ID
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<List<Document>> getPatientDocuments(@PathVariable Long patientId) {
        try {
            List<Document> documents = documentService.getDocumentsByPatientId(patientId);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Get documents by type
    @GetMapping("/doctor/{doctorId}/type/{documentType}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getDoctorDocumentsByType(
            @PathVariable Long doctorId, 
            @PathVariable String documentType) {
        try {
            Document.DocumentType type = Document.DocumentType.valueOf(documentType.toUpperCase());
            List<Document> documents = documentService.getDocumentsByDoctorIdAndType(doctorId, type);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/patient/{patientId}/type/{documentType}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
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
    
    // Update document description
    @PutMapping("/{documentId}/description")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<?> updateDocumentDescription(
            @PathVariable Long documentId,
            @RequestParam String description) {
        try {
            Document document = documentService.updateDocumentDescription(documentId, description);
            return ResponseEntity.ok(document);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Get recent documents
    @GetMapping("/recent")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<Document>> getRecentDocuments(
            @RequestParam(defaultValue = "7") int days) {
        try {
            List<Document> documents = documentService.getRecentDocuments(days);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}