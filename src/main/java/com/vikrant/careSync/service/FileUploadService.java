package com.vikrant.careSync.service;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.service.CloudinaryService;
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

    private final CloudinaryService cloudinaryService;

    // Using constants from AppConstants instead of @Value annotations
    private static final long MAX_FILE_SIZE = AppConstants.Config.MAX_FILE_SIZE;
    private static final String[] ALLOWED_DOCUMENT_EXTENSIONS = AppConstants.Config.ALLOWED_DOCUMENT_EXTENSIONS;
    private static final String[] ALLOWED_IMAGE_EXTENSIONS = AppConstants.Config.ALLOWED_IMAGE_EXTENSIONS;

    public String uploadCertificate(MultipartFile file, Long doctorId) throws IOException {
        validateFile(file);
        return cloudinaryService.uploadFile(file, CloudinaryService.FileType.CERTIFICATE, doctorId);
    }

    public String uploadProfileImage(MultipartFile file, Long userId, String userType) throws IOException {
        validateProfileImageFile(file);
        return cloudinaryService.uploadFile(file, CloudinaryService.FileType.PROFILE_IMAGE, userId);
    }

    public String uploadMedicalDocument(MultipartFile file, Long patientId) throws IOException {
        validateFile(file);
        return cloudinaryService.uploadFile(file, CloudinaryService.FileType.MEDICAL_DOCUMENT, patientId);
    }

    public void deleteFile(String cloudinaryUrl) throws IOException {
        String publicId = cloudinaryService.extractPublicId(cloudinaryUrl);
        if (publicId != null) {
            cloudinaryService.deleteFile(publicId);
        }
    }

    public boolean fileExists(String cloudinaryUrl) {
        String publicId = cloudinaryService.extractPublicId(cloudinaryUrl);
        return publicId != null && cloudinaryService.fileExists(publicId);
    }

    public long getFileSize(String cloudinaryUrl) throws IOException {
        // For Cloudinary URLs, we can't easily get file size without additional API calls
        // This would require implementing a separate method in CloudinaryService
        log.warn("File size retrieval not implemented for Cloudinary URLs: {}", cloudinaryUrl);
        return 0;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!isDocumentExtensionAllowed(extension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: " + String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS));
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
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
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

    public String getFileUrl(String cloudinaryUrl) {
        // For Cloudinary URLs, return the URL directly as it's already accessible
        return cloudinaryUrl;
    }

    public byte[] getFileContent(String cloudinaryUrl) throws IOException {
        // For Cloudinary URLs, we would need to download the file content
        // This is not commonly needed as Cloudinary URLs are directly accessible
        throw new UnsupportedOperationException("Direct file content retrieval not supported for Cloudinary URLs. Use the URL directly: " + cloudinaryUrl);
    }

    public void cleanupOrphanedFiles() throws IOException {
        // This method would clean up files that are no longer referenced in Cloudinary
        // Implementation would require checking database records against Cloudinary assets
        log.info("Cloudinary cleanup would require checking database records against Cloudinary assets");
    }
}