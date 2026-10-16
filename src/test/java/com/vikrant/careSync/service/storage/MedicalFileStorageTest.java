package com.vikrant.careSync.service.storage;

import com.vikrant.careSync.dto.DocumentDto;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Medical files live in the private bucket and leave the backend only as short-lived signed links. */
class MedicalFileStorageTest {

    private static final byte[] PDF = "%PDF-1.7\nhello".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

    private S3Client s3Client;
    private SupabaseStorageService storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create("http://localhost:9000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.AP_SOUTH_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        storage = new SupabaseStorageService(s3Client, presigner);
        ReflectionTestUtils.setField(storage, "bucketName", "public-bucket");
        ReflectionTestUtils.setField(storage, "publicUrlPrefix", "http://localhost:9000/public-bucket");
        ReflectionTestUtils.setField(storage, "privateBucketName", "medical-private");
        ReflectionTestUtils.setField(storage, "signedUrlTtlMinutes", 15L);
    }

    private static MockMultipartFile file(String name, String clientType, byte[] content) {
        return new MockMultipartFile("file", name, clientType, content);
    }

    @Test
    void labReportGoesToPrivateBucketWithDetectedContentType() throws Exception {
        SupabaseStorageService.StoredFile stored = storage.store(
                file("report.PDF", "text/html", PDF), SupabaseStorageService.FileType.LAB_REPORT, 201L);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        assertEquals("medical-private", put.getValue().bucket());
        assertEquals("application/pdf", put.getValue().contentType(), "client's text/html claim is ignored");
        assertTrue(put.getValue().key().startsWith("lab_reports/user_201/") && put.getValue().key().endsWith(".pdf"));
        assertTrue(stored.filePath().startsWith(SupabaseStorageService.PRIVATE_PREFIX));
    }

    @Test
    void privateFilesAreServedThroughExpiringSignedLinks() {
        String url = storage.accessUrl("private:lab_reports/user_201/abc.pdf", "my report.pdf");
        assertTrue(url.contains("/medical-private/lab_reports/user_201/abc.pdf"));
        assertTrue(url.contains("X-Amz-Signature="));
        assertTrue(url.contains("X-Amz-Expires=900"), "15 minutes");
        assertTrue(url.contains("response-content-disposition"));

        String publicUrl = "http://localhost:9000/public-bucket/profiles/user_1/a.png";
        assertEquals(publicUrl, storage.accessUrl(publicUrl, null), "public files keep their URL");
    }

    @Test
    void profileImagesStayPublic() throws Exception {
        String path = storage.store(file("me.png", "image/png", PNG),
                SupabaseStorageService.FileType.PROFILE_IMAGE, 1L).filePath();
        assertTrue(path.startsWith("http://localhost:9000/public-bucket/profiles/user_1/"));
    }

    @Test
    void withoutPrivateBucketMedicalFilesKeepOldBehaviour() throws Exception {
        ReflectionTestUtils.setField(storage, "privateBucketName", "");
        String path = storage.store(file("r.pdf", "application/pdf", PDF),
                SupabaseStorageService.FileType.LAB_REPORT, 1L).filePath();
        assertTrue(path.startsWith("http://localhost:9000/public-bucket/"));
    }

    @Test
    void disguisedFilesAreRejectedBeforeUpload() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> storage.store(file("report.pdf", "application/pdf", html),
                SupabaseStorageService.FileType.LAB_REPORT, 1L));
        assertThrows(IllegalArgumentException.class, () -> storage.store(file("notes.txt", "text/plain", html),
                SupabaseStorageService.FileType.MEDICAL_DOCUMENT, 1L));
        assertThrows(IllegalArgumentException.class, () -> storage.store(file("photo.jpg", "image/jpeg", PNG),
                SupabaseStorageService.FileType.PROFILE_IMAGE, 1L));
        assertThrows(IllegalArgumentException.class, () -> storage.store(file("drawing.svg", "image/svg+xml", html),
                SupabaseStorageService.FileType.PROFILE_IMAGE, 1L));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void validFilesOfEachKindPass() throws Exception {
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileContentValidator.inspect(file("a.docx", "x", new byte[] { 'P', 'K', 3, 4, 0 })).contentType());
        assertEquals("text/plain", FileContentValidator.inspect(file("a.txt", "x",
                "BP 120/80, follow up in 2 weeks".getBytes(StandardCharsets.UTF_8))).contentType());
        assertEquals("image/jpeg", FileContentValidator.inspect(file("a.jpeg", "x",
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 })).contentType());
    }

    @Test
    void filenamesAreSafeForHeaders() {
        assertEquals("report_.pdf", FileContentValidator.safeFilename("C:\\\\x\\report\".pdf"));
        assertEquals("a_b.pdf", FileContentValidator.safeFilename("../../a\r\nb.pdf").replace("__", "_"));
    }

    @Test
    void cachedDocumentListsGetFreshSignedLinks() {
        DocumentService documentService = new DocumentService(mock(DocumentRepository.class),
                mock(DoctorRepository.class), mock(PatientRepository.class), mock(BookingRepository.class), storage);
        DocumentDto cached = DocumentDto.builder().filename("r.pdf").url("private:lab_reports/user_1/r.pdf")
                .fileUrl("private:lab_reports/user_1/r.pdf").downloadUrl("private:lab_reports/user_1/r.pdf").build();

        DocumentDto out = documentService.withFreshUrls(new ArrayList<>(List.of(cached))).get(0);

        assertTrue(out.getUrl().contains("X-Amz-Signature="));
        assertTrue(out.getDownloadUrl().contains("response-content-disposition"));
        assertFalse(out.getFileUrl().startsWith("private:"));
    }

    @Test
    void migrationMovesOnlyActivePublicMedicalFiles() {
        DocumentRepository repository = mock(DocumentRepository.class);
        Document labReport = Document.builder().id(1L).documentType(Document.DocumentType.LAB_REPORT).isActive(true)
                .filePath("http://localhost:9000/public-bucket/lab_reports/user_1/a.pdf").build();
        Document profile = Document.builder().id(2L).documentType(Document.DocumentType.PROFILE_IMAGE).isActive(true)
                .filePath("http://localhost:9000/public-bucket/profiles/user_1/a.png").build();
        Document deleted = Document.builder().id(3L).documentType(Document.DocumentType.PRESCRIPTION).isActive(false)
                .filePath("http://localhost:9000/public-bucket/prescriptions/user_1/b.pdf").build();
        when(repository.findAll()).thenReturn(List.of(labReport, profile, deleted));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("application/pdf").build(), PDF));
        MedicalFileMigrationService migration = new MedicalFileMigrationService(repository, storage);

        Map<String, Object> dryRun = migration.moveMedicalFilesToPrivateBucket(true, false);
        assertEquals(1, dryRun.get("medicalFilesInPublicBucket"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        Map<String, Object> run = migration.moveMedicalFilesToPrivateBucket(false, false);
        assertEquals(1, run.get("moved"));
        assertEquals("private:lab_reports/user_1/a.pdf", labReport.getFilePath());
        assertTrue(profile.getFilePath().startsWith("http"), "profile images stay public");
        verify(s3Client, never()).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    void certificateReviewOnlyReadsOurOwnCertificateFiles() throws Exception {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(inv -> new software.amazon.awssdk.core
                .ResponseInputStream<>(GetObjectResponse.builder().build(), new java.io.ByteArrayInputStream(PDF)));

        // Links outside our storage are never fetched (no outgoing request at all)
        for (String link : List.of("http://169.254.169.254/latest/meta-data/", "https://evil.example/cert.pdf",
                "http://localhost:9000/public-bucket-evil/certificates/x.pdf")) {
            assertTrue(storage.readOwnFile(link, SupabaseStorageService.FileType.CERTIFICATE, 1000).isEmpty(), link);
        }
        // Our storage, but outside the certificates folder or with path tricks
        assertTrue(storage.readOwnFile("http://localhost:9000/public-bucket/medical_documents/user_1/a.pdf",
                SupabaseStorageService.FileType.CERTIFICATE, 1000).isEmpty());
        assertTrue(storage.readOwnFile("http://localhost:9000/public-bucket/certificates/../lab_reports/a.pdf",
                SupabaseStorageService.FileType.CERTIFICATE, 1000).isEmpty());
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));

        assertArrayEquals(PDF, storage.readOwnFile("http://localhost:9000/public-bucket/certificates/user_1/c.pdf",
                SupabaseStorageService.FileType.CERTIFICATE, 1000).orElseThrow());
        assertTrue(storage.readOwnFile("http://localhost:9000/public-bucket/certificates/user_1/c.pdf",
                SupabaseStorageService.FileType.CERTIFICATE, 5).isEmpty(), "too large");
    }
}
