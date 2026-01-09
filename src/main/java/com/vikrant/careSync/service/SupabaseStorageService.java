package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageService {

    private final S3Client s3Client;

    @Value("${app.supabase.s3.bucket}")
    private String bucketName;

    @Value("${app.supabase.s3.public-url-prefix}")
    private String publicUrlPrefix;

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
     * Upload file to Supabase Storage
     */
    public String uploadFile(MultipartFile file, FileType fileType, Long userId) throws IOException {
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            String key = fileType.getFolderName();
            if (userId != null) {
                key += "/user_" + userId;
            }
            key += "/" + uniqueFilename;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            String fileUrl = publicUrlPrefix + "/" + key;
            log.info("File uploaded successfully to Supabase Storage: {}", fileUrl);

            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Supabase: " + e.getMessage(), e);
        }
    }

    public String uploadFile(MultipartFile file, FileType fileType) throws IOException {
        return uploadFile(file, fileType, null);
    }

    /**
     * Delete file from Supabase Storage
     */
    public boolean deleteFile(String key) {
        try {
            // If full URL is passed, extract key
            if (key.startsWith("http")) {
                key = extractKey(key);
            }

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted from Supabase: {}", key);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete file from Supabase: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract key from Supabase URL
     */
    public String extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(publicUrlPrefix)) {
            return fileUrl;
        }
        return fileUrl.replace(publicUrlPrefix + "/", "");
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String key) {
        try {
            if (key.startsWith("http")) {
                key = extractKey(key);
            }
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
