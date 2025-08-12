package com.vikrant.careSync.controller;

import com.vikrant.careSync.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload/certificate")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> uploadCertificate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doctorId") Long doctorId) {
        try {
            String filePath = fileUploadService.uploadCertificate(file, doctorId);
            String fileUrl = fileUploadService.getFileUrl(filePath);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload certificate: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        }
    }

    @PostMapping("/upload/profile-image")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<String> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam("userType") String userType) {
        try {
            String filePath = fileUploadService.uploadProfileImage(file, userId, userType);
            String fileUrl = fileUploadService.getFileUrl(filePath);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload profile image: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        }
    }

    @PostMapping("/upload/medical-document")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<String> uploadMedicalDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") Long patientId) {
        try {
            String filePath = fileUploadService.uploadMedicalDocument(file, patientId);
            String fileUrl = fileUploadService.getFileUrl(filePath);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to upload medical document: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{filePath:.+}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable String filePath) {
        try {
            byte[] fileContent = fileUploadService.getFileContent(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath + "\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/view/{filePath:.+}")
    public ResponseEntity<ByteArrayResource> viewFile(@PathVariable String filePath) {
        try {
            byte[] fileContent = fileUploadService.getFileContent(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            // Determine content type based on file extension
            String contentType = determineContentType(filePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{filePath:.+}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('PATIENT')")
    public ResponseEntity<?> deleteFile(@PathVariable String filePath) {
        try {
            fileUploadService.deleteFile(filePath);
            return ResponseEntity.ok().body("File deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to delete file: " + e.getMessage());
        }
    }

    @GetMapping("/exists/{filePath:.+}")
    public ResponseEntity<Boolean> fileExists(@PathVariable String filePath) {
        boolean exists = fileUploadService.fileExists(filePath);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/size/{filePath:.+}")
    public ResponseEntity<Long> getFileSize(@PathVariable String filePath) {
        try {
            long size = fileUploadService.getFileSize(filePath);
            return ResponseEntity.ok(size);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/url/{filePath:.+}")
    public ResponseEntity<String> getFileUrl(@PathVariable String filePath) {
        String url = fileUploadService.getFileUrl(filePath);
        return ResponseEntity.ok(url);
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> cleanupOrphanedFiles() {
        try {
            fileUploadService.cleanupOrphanedFiles();
            return ResponseEntity.ok().body("Cleanup completed successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to cleanup files: " + e.getMessage());
        }
    }

    private String determineContentType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
} 