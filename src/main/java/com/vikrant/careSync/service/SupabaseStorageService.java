package com.vikrant.careSync.service;

import com.vikrant.careSync.service.storage.FileContentValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import com.vikrant.careSync.util.ErrorLog;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageService {

    /** Marks a stored file path as living in the private bucket; such files are only reachable via signed links. */
    public static final String PRIVATE_PREFIX = "private:";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.supabase.s3.bucket}")
    private String bucketName;

    @Value("${app.supabase.s3.public-url-prefix}")
    private String publicUrlPrefix;

    // Bucket with public access OFF for medical files. Empty: medical files still go to the public bucket.
    @Value("${app.supabase.s3.private-bucket:}")
    private String privateBucketName;

    @Value("${app.storage.signed-url-ttl-minutes:15}")
    private long signedUrlTtlMinutes;

    public enum FileType {
        PROFILE_IMAGE("profiles", false),
        CERTIFICATE("certificates", false),
        MEDICAL_DOCUMENT("medical_documents", true),
        PRESCRIPTION("prescriptions", true),
        LAB_REPORT("lab_reports", true),
        INSURANCE_DOCUMENT("insurance_documents", true),
        IDENTIFICATION("identification", true),
        OTHER("other", true);

        private final String folderName;
        private final boolean medical;

        FileType(String folderName, boolean medical) {
            this.folderName = folderName;
            this.medical = medical;
        }

        public String getFolderName() {
            return folderName;
        }

        /** Medical and identity files are patient data and belong in the private bucket. */
        public boolean isMedical() {
            return medical;
        }
    }

    /** Where an upload was stored: the path to save on the record, the object key and the verified content type. */
    public record StoredFile(String filePath, String key, String contentType) {
    }

    @PostConstruct
    void reportPrivateBucket() {
        if (!isPrivateBucketConfigured()) {
            log.warn("[ServiceStatus] - File storage    : no private bucket (SUPABASE_S3_PRIVATE_BUCKET); medical files "
                    + "are stored in the public bucket and reachable by anyone holding the link");
        }
    }

    public boolean isPrivateBucketConfigured() {
        return privateBucketName != null && !privateBucketName.isBlank();
    }

    /**
     * Upload file to Supabase Storage. The content must match the extension; the stored content type and
     * extension come from the check, not from the client.
     */
    public StoredFile store(MultipartFile file, FileType fileType, Long userId) throws IOException {
        FileContentValidator.DetectedFile detected = FileContentValidator.inspect(file);
        boolean toPrivate = fileType.isMedical() && isPrivateBucketConfigured();
        try {
            String key = fileType.getFolderName();
            if (userId != null) {
                key += "/user_" + userId;
            }
            key += "/" + UUID.randomUUID() + "." + detected.extension();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(toPrivate ? privateBucketName : bucketName)
                    .key(key)
                    .contentType(detected.contentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            String filePath = toPrivate ? PRIVATE_PREFIX + key : publicUrlPrefix + "/" + key;
            log.info("File uploaded to Supabase Storage ({} bucket): {}", toPrivate ? "private" : "public", key);
            return new StoredFile(filePath, key, detected.contentType());
        } catch (Exception e) {
            ErrorLog.error(e, "Failed to upload file to Supabase");
            throw new IOException("Failed to upload file to Supabase: " + e.getMessage(), e);
        }
    }

    public boolean isPrivate(String filePath) {
        return filePath != null && filePath.startsWith(PRIVATE_PREFIX);
    }

    /**
     * A link the browser can open. Public files keep their permanent URL; private files get a signed link that
     * expires after app.storage.signed-url-ttl-minutes.
     */
    public String accessUrl(String filePath, String downloadFilename) {
        if (!isPrivate(filePath)) {
            return filePath;
        }
        GetObjectRequest.Builder get = GetObjectRequest.builder()
                .bucket(privateBucketName)
                .key(filePath.substring(PRIVATE_PREFIX.length()));
        if (downloadFilename != null) {
            get.responseContentDisposition("attachment; filename=\""
                    + FileContentValidator.safeFilename(downloadFilename) + "\"");
        }
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(signedUrlTtlMinutes))
                .getObjectRequest(get.build())
                .build()).url().toString();
    }

    /** Streams a stored file (public or private) through the backend. */
    public InputStream openStream(String filePath) {
        boolean privateFile = isPrivate(filePath);
        String key = privateFile ? filePath.substring(PRIVATE_PREFIX.length()) : extractKey(filePath);
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(privateFile ? privateBucketName : bucketName)
                .key(key)
                .build());
    }

    /** Deletes a stored file from whichever bucket holds it. */
    public boolean deleteStoredFile(String filePath) {
        if (!isPrivate(filePath)) {
            return deleteFile(filePath);
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(privateBucketName)
                    .key(filePath.substring(PRIVATE_PREFIX.length()))
                    .build());
            return true;
        } catch (Exception e) {
            ErrorLog.error(e, "Failed to delete private file from Supabase");
            return false;
        }
    }

    /**
     * Copies a file from the public bucket into the private bucket under the same key.
     * @return the private file path to store on the record
     */
    public String copyToPrivate(String publicFilePath) {
        if (!isPrivateBucketConfigured()) {
            throw new IllegalStateException("No private bucket configured");
        }
        String key = extractKey(publicFilePath);
        ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(privateBucketName)
                .key(key)
                .contentType(object.response().contentType())
                .build(), RequestBody.fromBytes(object.asByteArray()));
        return PRIVATE_PREFIX + key;
    }

    /**
     * Reads a file only if it is one of ours: a URL under our public bucket prefix (or a private path) whose key
     * lies in the given folder. Anything else returns empty, so a user-supplied link can never make the server
     * call an arbitrary address. Files larger than maxBytes are not read.
     */
    public java.util.Optional<byte[]> readOwnFile(String filePath, FileType folder, long maxBytes) {
        if (filePath == null) {
            return java.util.Optional.empty();
        }
        boolean privateFile = isPrivate(filePath);
        if (!privateFile && !filePath.startsWith(publicUrlPrefix + "/")) {
            return java.util.Optional.empty();
        }
        String key = extractKey(filePath);
        if (key == null || !key.startsWith(folder.getFolderName() + "/") || key.contains("..")
                || key.contains("?") || key.contains("#")) {
            return java.util.Optional.empty();
        }
        try (InputStream in = s3Client.getObject(GetObjectRequest.builder()
                .bucket(privateFile ? privateBucketName : bucketName)
                .key(key)
                .build())) {
            byte[] data = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
            return data.length == 0 || data.length > maxBytes ? java.util.Optional.empty()
                    : java.util.Optional.of(data);
        } catch (Exception e) {
            ErrorLog.warn(e, "Could not read stored file {}", key);
            return java.util.Optional.empty();
        }
    }

    /**
     * Delete file from the public bucket
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
            ErrorLog.error(e, "Failed to delete file from Supabase");
            return false;
        }
    }

    /**
     * Extract key from Supabase URL
     */
    public String extractKey(String fileUrl) {
        if (isPrivate(fileUrl)) {
            return fileUrl.substring(PRIVATE_PREFIX.length());
        }
        if (fileUrl == null || !fileUrl.contains(publicUrlPrefix)) {
            return fileUrl;
        }
        return fileUrl.replace(publicUrlPrefix + "/", "");
    }

}
