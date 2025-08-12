package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    @Value("${app.file.upload.path:uploads/}")
    private String uploadPath;

    @Value("${app.file.max-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${app.file.allowed-extensions:jpg,jpeg,png,pdf,doc,docx}")
    private String allowedExtensions;

    public String uploadCertificate(MultipartFile file, Long doctorId) throws IOException {
        validateFile(file);
        String fileName = generateFileName(file, "certificate", doctorId);
        return saveFile(file, fileName, "certificates");
    }

    public String uploadProfileImage(MultipartFile file, Long userId, String userType) throws IOException {
        validateFile(file);
        validateImageFile(file);
        String fileName = generateFileName(file, "profile", userId);
        return saveFile(file, fileName, "profiles/" + userType.toLowerCase());
    }

    public String uploadMedicalDocument(MultipartFile file, Long patientId) throws IOException {
        validateFile(file);
        String fileName = generateFileName(file, "medical", patientId);
        return saveFile(file, fileName, "medical-documents");
    }

    public void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(uploadPath, filePath);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    public boolean fileExists(String filePath) {
        Path path = Paths.get(uploadPath, filePath);
        return Files.exists(path);
    }

    public long getFileSize(String filePath) throws IOException {
        Path path = Paths.get(uploadPath, filePath);
        if (Files.exists(path)) {
            return Files.size(path);
        }
        return 0;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: " + allowedExtensions);
        }
    }

    private void validateImageFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!extension.matches("jpg|jpeg|png|gif")) {
                throw new IllegalArgumentException("Only image files (jpg, jpeg, png, gif) are allowed for profile images");
            }
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private boolean isAllowedExtension(String extension) {
        String[] allowed = allowedExtensions.split(",");
        for (String ext : allowed) {
            if (ext.trim().equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private String generateFileName(MultipartFile file, String type, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        return String.format("%s_%s_%s_%s.%s", type, userId, timestamp, uniqueId, extension);
    }

    private String saveFile(MultipartFile file, String fileName, String subDirectory) throws IOException {
        Path uploadDir = Paths.get(uploadPath, subDirectory);
        
        // Create directory if it doesn't exist
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        Path filePath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return subDirectory + "/" + fileName;
    }

    public String getFileUrl(String filePath) {
        // In a real application, this would return the actual URL
        // For now, returning a relative path
        return "/api/files/" + filePath;
    }

    public byte[] getFileContent(String filePath) throws IOException {
        Path path = Paths.get(uploadPath, filePath);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new IOException("File not found: " + filePath);
    }

    public void cleanupOrphanedFiles() throws IOException {
        // This method would clean up files that are no longer referenced
        // Implementation would depend on your specific requirements
        Path uploadDir = Paths.get(uploadPath);
        if (Files.exists(uploadDir)) {
            // Add logic to find and remove orphaned files
            System.out.println("Cleaning up orphaned files...");
        }
    }
} 