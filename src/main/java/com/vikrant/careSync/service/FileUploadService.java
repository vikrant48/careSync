package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final SupabaseStorageService supabaseStorageService;

    // Using constants from AppConstants instead of @Value annotations
    private static final long MAX_FILE_SIZE = AppConstants.Config.MAX_FILE_SIZE;
    private static final String[] ALLOWED_DOCUMENT_EXTENSIONS = AppConstants.Config.ALLOWED_DOCUMENT_EXTENSIONS;
    private static final String[] ALLOWED_IMAGE_EXTENSIONS = AppConstants.Config.ALLOWED_IMAGE_EXTENSIONS;

    public String uploadCertificate(MultipartFile file, Long doctorId) throws IOException {
        validateFile(file);
        return supabaseStorageService.uploadFile(file, SupabaseStorageService.FileType.CERTIFICATE, doctorId);
    }

    public String uploadProfileImage(MultipartFile file, Long userId, String userType) throws IOException {
        validateProfileImageFile(file);
        return supabaseStorageService.uploadFile(file, SupabaseStorageService.FileType.PROFILE_IMAGE, userId);
    }

    public String uploadMedicalDocument(MultipartFile file, Long patientId) throws IOException {
        validateFile(file);
        return supabaseStorageService.uploadFile(file, SupabaseStorageService.FileType.MEDICAL_DOCUMENT, patientId);
    }

    public void deleteFile(String fileUrl) throws IOException {
        String key = supabaseStorageService.extractKey(fileUrl);
        if (key != null) {
            supabaseStorageService.deleteFile(key);
        }
    }

    public boolean fileExists(String fileUrl) {
        String key = supabaseStorageService.extractKey(fileUrl);
        return key != null && supabaseStorageService.fileExists(key);
    }

    public long getFileSize(String fileUrl) throws IOException {
        // For Supabase URLs, we can't easily get file size without additional API
        // calls
        // This would require implementing a separate method in SupabaseStorageService
        log.warn("File size retrieval not implemented for Supabase URLs: {}", fileUrl);
        return 0;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!isDocumentExtensionAllowed(extension)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed types: " + String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS));
        }
    }

    private void validateImageFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename);
            if (!isImageExtensionAllowed(extension)) {
                throw new IllegalArgumentException("Only image files (" +
                        String.join(", ", ALLOWED_IMAGE_EXTENSIONS) + ") are allowed");
            }
        }
    }

    private void validateProfileImageFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!isImageExtensionAllowed(extension)) {
            throw new IllegalArgumentException("Only image files (" +
                    String.join(", ", ALLOWED_IMAGE_EXTENSIONS) + ") are allowed for profile images");
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private boolean isDocumentExtensionAllowed(String extension) {
        for (String ext : ALLOWED_DOCUMENT_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isImageExtensionAllowed(String extension) {
        for (String ext : ALLOWED_IMAGE_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
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

    public String getFileUrl(String fileUrl) {
        // For Supabase URLs, return the URL directly as it's already accessible
        return fileUrl;
    }

    public byte[] getFileContent(String fileUrl) throws IOException {
        // For Supabase URLs, we would need to download the file content
        // This is not commonly needed as URLs are directly accessible
        throw new UnsupportedOperationException(
                "Direct file content retrieval not supported for Supabase URLs. Use the URL directly: " + fileUrl);
    }

    public void cleanupOrphanedFiles() throws IOException {
        // This method would clean up files that are no longer referenced in Supabase
        // Implementation would require checking database records against Supabase
        // assets
        log.info("Supabase cleanup would require checking database records against Supabase assets");
    }
}