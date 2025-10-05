package com.vikrant.careSync.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public enum FileType {
        PROFILE_IMAGE("profiles"),
        CERTIFICATE("certificates"),
        MEDICAL_DOCUMENT("medical_documents"),
        PRESCRIPTION("prescriptions"),
        LAB_REPORT("lab_reports"),
        INSURANCE_DOCUMENT("insurance_documents"),
        IDENTIFICATION("identification"),
        OTHER("other");

        private final String folderName;

        FileType(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    /**
     * Upload file to Cloudinary with organized folder structure
     * @param file The file to upload
     * @param fileType The type of file (determines folder)
     * @param userId Optional user ID for further organization
     * @return The secure URL of the uploaded file
     * @throws IOException if upload fails
     */
    public String uploadFile(MultipartFile file, FileType fileType, Long userId) throws IOException {
        try {
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // Create folder path: fileType/userId (if provided)
            String folderPath = fileType.getFolderName();
            if (userId != null) {
                folderPath += "/user_" + userId;
            }

            // Upload parameters
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", uniqueFilename.substring(0, uniqueFilename.lastIndexOf('.')),
                    "resource_type", "auto",
                    "use_filename", false,
                    "unique_filename", true
            );

            // Upload to Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            
            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("File uploaded successfully to Cloudinary: {}", secureUrl);
            
            return secureUrl;
            
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file to Cloudinary without user-specific folder
     * @param file The file to upload
     * @param fileType The type of file (determines folder)
     * @return The secure URL of the uploaded file
     * @throws IOException if upload fails
     */
    public String uploadFile(MultipartFile file, FileType fileType) throws IOException {
        return uploadFile(file, fileType, null);
    }

    /**
     * Delete file from Cloudinary
     * @param publicId The public ID of the file to delete
     * @return true if deletion was successful
     */
    public boolean deleteFile(String publicId) {
        try {
            Map<String, Object> deleteResult = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String result = (String) deleteResult.get("result");
            log.info("File deletion result: {}", result);
            return "ok".equals(result);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract public ID from Cloudinary URL
     * @param cloudinaryUrl The full Cloudinary URL
     * @return The public ID
     */
    public String extractPublicId(String cloudinaryUrl) {
        if (cloudinaryUrl == null || !cloudinaryUrl.contains("cloudinary.com")) {
            return null;
        }
        
        try {
            // Extract public ID from URL
            // URL format: https://res.cloudinary.com/cloud_name/image/upload/v1234567890/folder/public_id.extension
            String[] parts = cloudinaryUrl.split("/");
            String lastPart = parts[parts.length - 1];
            
            // Remove file extension
            if (lastPart.contains(".")) {
                lastPart = lastPart.substring(0, lastPart.lastIndexOf("."));
            }
            
            // Reconstruct public ID with folder path
            StringBuilder publicId = new StringBuilder();
            boolean foundUpload = false;
            for (int i = 0; i < parts.length - 1; i++) {
                if (foundUpload && !parts[i].startsWith("v")) {
                    if (publicId.length() > 0) {
                        publicId.append("/");
                    }
                    publicId.append(parts[i]);
                }
                if ("upload".equals(parts[i])) {
                    foundUpload = true;
                }
            }
            
            if (publicId.length() > 0) {
                publicId.append("/").append(lastPart);
            } else {
                publicId.append(lastPart);
            }
            
            return publicId.toString();
        } catch (Exception e) {
            log.error("Failed to extract public ID from URL: {}", cloudinaryUrl, e);
            return null;
        }
    }

    /**
     * Check if file exists in Cloudinary
     * @param publicId The public ID to check
     * @return true if file exists
     */
    public boolean fileExists(String publicId) {
        try {
            Map<String, Object> result = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            return result != null && result.containsKey("public_id");
        } catch (Exception e) {
            log.debug("File does not exist or error checking: {}", publicId);
            return false;
        }
    }
}