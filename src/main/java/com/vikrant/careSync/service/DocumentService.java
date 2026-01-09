package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Booking;

import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.dto.DocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final SupabaseStorageService supabaseStorageService;

    // Using constants from AppConstants instead of @Value annotation
    private static final long MAX_FILE_SIZE = AppConstants.Config.MAX_FILE_SIZE;
    private static final String[] ALLOWED_DOCUMENT_EXTENSIONS = AppConstants.Config.ALLOWED_DOCUMENT_EXTENSIONS;
    private static final String[] ALLOWED_IMAGE_EXTENSIONS = AppConstants.Config.ALLOWED_IMAGE_EXTENSIONS;

    /**
     * Upload a document for a doctor
     */
    public Document uploadDocumentForDoctor(MultipartFile file, Long doctorId,
            Document.DocumentType documentType,
            String description, String uploadedByUsername, String uploadedByType) throws IOException {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + doctorId));

        return uploadDocument(file, doctor, null, null, documentType, description, uploadedByUsername, uploadedByType);
    }

    /**
     * Upload a document for a patient
     */
    public Document uploadDocumentForPatient(MultipartFile file, Long patientId,
            Document.DocumentType documentType,
            String description, String uploadedByUsername, String uploadedByType) throws IOException {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + patientId));

        return uploadDocument(file, null, patient, null, documentType, description, uploadedByUsername, uploadedByType);
    }

    /**
     * Upload a lab report linked to a booking
     */
    public Document uploadLabReport(MultipartFile file, Long bookingId, Long patientId,
            String description, String uploadedByUsername, String uploadedByType) throws IOException {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + bookingId));

        // specific validation that the booking belongs to the patient
        if (!booking.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Booking does not belong to the specified patient");
        }

        Patient patient = booking.getPatient();
        Doctor doctor = booking.getDoctor(); // Get doctor if assigned

        return uploadDocument(file, doctor, patient, booking, Document.DocumentType.LAB_REPORT, description,
                uploadedByUsername, uploadedByType);
    }

    /**
     * Core upload method
     */
    @CacheEvict(value = "patientData", allEntries = true) // Document upload affects listings
    private Document uploadDocument(MultipartFile file, Doctor doctor, Patient patient, Booking booking,
            Document.DocumentType documentType, String description,
            String uploadedByUsername, String uploadedByType) throws IOException {
        // Validate file based on document type
        validateFileByType(file, documentType);

        // Map document type to Supabase file type
        SupabaseStorageService.FileType supabaseFileType = mapDocumentTypeToSupabaseFileType(documentType);

        // Determine user ID for folder organization
        Long userId = doctor != null ? doctor.getId() : (patient != null ? patient.getId() : null);

        // Upload to Supabase
        String supabaseUrl = supabaseStorageService.uploadFile(file, supabaseFileType, userId);

        // Extract key from URL
        String fileKey = supabaseStorageService.extractKey(supabaseUrl);

        // Create document metadata
        Document document = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(fileKey) // Store Supabase key
                .filePath(supabaseUrl) // Store Supabase URL
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .documentType(documentType)
                .description(description)
                .doctor(doctor)
                .patient(patient)
                .booking(booking)
                .uploadedByUsername(uploadedByUsername)
                .uploadedByType(uploadedByType)
                .isActive(true)
                .build();

        // Save to database
        Document savedDocument = documentRepository.save(document);

        log.info("Document uploaded successfully to Supabase: {} for {} {}",
                savedDocument.getOriginalFilename(),
                doctor != null ? "doctor" : "patient",
                doctor != null ? doctor.getId() : patient.getId());

        return savedDocument;
    }

    /**
     * Get document by ID
     */
    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    @Cacheable(value = "doctorListing", key = "'docs_' + #doctorId")
    public List<DocumentDto> getDocumentsDtoByDoctorId(Long doctorId) {
        return documentRepository.findByDoctorId(doctorId).stream()
                .map(doc -> new DocumentDto(doc, doc.getFilePath(), doc.getFilePath()))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "patientData", key = "'docs_' + #patientId")
    public List<DocumentDto> getDocumentsDtoByPatientId(Long patientId) {
        return documentRepository.findByPatientId(patientId).stream()
                .map(doc -> new DocumentDto(doc, doc.getFilePath(), doc.getFilePath()))
                .collect(Collectors.toList());
    }

    public List<Document> getDocumentsByDoctorId(Long doctorId) {
        return documentRepository.findByDoctorId(doctorId);
    }

    public List<Document> getDocumentsByPatientId(Long patientId) {
        return documentRepository.findByPatientId(patientId);
    }

    /**
     * Get documents by doctor ID and type
     */
    public List<Document> getDocumentsByDoctorIdAndType(Long doctorId, Document.DocumentType documentType) {
        return documentRepository.findByDoctorIdAndDocumentType(doctorId, documentType);
    }

    /**
     * Get documents by patient ID and type
     */
    public List<Document> getDocumentsByPatientIdAndType(Long patientId, Document.DocumentType documentType) {
        return documentRepository.findByPatientIdAndDocumentType(patientId, documentType);
    }

    /**
     * Get file as resource for download from Cloudinary
     */
    public Resource getFileAsResource(Long documentId) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));

        // For Supabase URLs, create a URL resource directly
        URL supabaseUrl = new URL(document.getFilePath());
        Resource resource = new UrlResource(supabaseUrl);

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("File not found or not readable from Supabase: " + document.getFilePath());
        }
    }

    /**
     * Delete document (soft delete)
     */
    @CacheEvict(value = { "patientData", "doctorListing" }, allEntries = true)
    public void deleteDocument(Long documentId) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));

        // Soft delete in database
        document.setIsActive(false);
        documentRepository.save(document);

        // Delete file from Supabase using the stored filename (key)
        try {
            supabaseStorageService.deleteFile(document.getStoredFilename());
            log.info("File deleted from Supabase: {}", document.getStoredFilename());
        } catch (Exception e) {
            log.warn("Failed to delete file from Supabase: {}", document.getStoredFilename(), e);
        }
    }

    /**
     * Update document description
     */
    public Document updateDocumentDescription(Long documentId, String description) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));

        document.setDescription(description);
        return documentRepository.save(document);
    }

    /**
     * Get recent documents
     */
    public List<Document> getRecentDocuments(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return documentRepository.findRecentDocuments(since);
    }

    /**
     * Validate uploaded file based on document type
     */
    private void validateFileByType(MultipartFile file, Document.DocumentType documentType) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " +
                    (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }

        String extension = getFileExtension(filename);

        // Validate extension based on document type
        if (documentType == Document.DocumentType.PROFILE_IMAGE) {
            if (!isImageExtensionAllowed(extension)) {
                throw new IllegalArgumentException("File type (." + extension + ") not allowed. Allowed types: " +
                        String.join(", ", ALLOWED_IMAGE_EXTENSIONS));
            }
        } else {
            if (!isDocumentExtensionAllowed(extension)) {
                throw new IllegalArgumentException("File type (." + extension + ") not allowed. Allowed types: " +
                        String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS));
            }
        }

        // Additional content type validation
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(contentType, documentType)) {
            throw new IllegalArgumentException("Invalid file content type: " + contentType);
        }
    }

    /**
     * Validate uploaded file (legacy method for backward compatibility)
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " +
                    (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }

        String extension = getFileExtension(filename);
        if (!isDocumentExtensionAllowed(extension)) {
            throw new IllegalArgumentException("File type (." + extension + ") not allowed. Allowed types: " +
                    String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS));
        }

        // Additional content type validation
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("Invalid file content type: " + contentType);
        }
    }

    /**
     * Check if document extension is allowed
     */
    private boolean isDocumentExtensionAllowed(String extension) {
        for (String ext : ALLOWED_DOCUMENT_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if image extension is allowed
     */
    private boolean isImageExtensionAllowed(String extension) {
        for (String ext : ALLOWED_IMAGE_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if content type is allowed based on document type
     */
    private boolean isAllowedContentType(String contentType, Document.DocumentType documentType) {
        if (documentType == Document.DocumentType.PROFILE_IMAGE) {
            return contentType.startsWith("image/");
        }
        return isAllowedContentType(contentType);
    }

    /**
     * Check if content type is allowed (legacy method)
     */
    private boolean isAllowedContentType(String contentType) {
        return contentType.startsWith("image/") ||
                contentType.equals("application/pdf") ||
                contentType.startsWith("application/msword") ||
                contentType.startsWith("application/vnd.openxmlformats-officedocument");
    }

    /**
     * Generate unique filename
     */
    private String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid + "." + extension;
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * Map document type to Supabase file type
     */
    private SupabaseStorageService.FileType mapDocumentTypeToSupabaseFileType(Document.DocumentType documentType) {
        return switch (documentType) {
            case PROFILE_IMAGE -> SupabaseStorageService.FileType.PROFILE_IMAGE;
            case CERTIFICATE -> SupabaseStorageService.FileType.CERTIFICATE;
            case MEDICAL_DOCUMENT -> SupabaseStorageService.FileType.MEDICAL_DOCUMENT;
            case PRESCRIPTION -> SupabaseStorageService.FileType.PRESCRIPTION;
            case LAB_REPORT -> SupabaseStorageService.FileType.LAB_REPORT;
            case INSURANCE_DOCUMENT -> SupabaseStorageService.FileType.INSURANCE_DOCUMENT;
            case IDENTIFICATION -> SupabaseStorageService.FileType.IDENTIFICATION;
            case OTHER -> SupabaseStorageService.FileType.OTHER;
        };
    }

    /**
     * Get file URL for frontend access (Direct Supabase URL)
     */
    public String getFileUrl(Long documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getFilePath)
                .orElse("/api/files/view/" + documentId);
    }

    /**
     * Get download URL for frontend access (Direct Supabase URL)
     */
    public String getDownloadUrl(Long documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getFilePath)
                .orElse("/api/files/download/" + documentId);
    }
}