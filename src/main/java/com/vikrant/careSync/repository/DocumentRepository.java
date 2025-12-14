package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d FROM Document d WHERE d.doctor.id = :doctorId AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT d FROM Document d WHERE d.patient.id = :patientId AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT d FROM Document d WHERE d.doctor.id = :doctorId AND d.documentType = :documentType AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByDoctorIdAndDocumentType(@Param("doctorId") Long doctorId,
            @Param("documentType") Document.DocumentType documentType);

    @Query("SELECT d FROM Document d WHERE d.patient.id = :patientId AND d.documentType = :documentType AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByPatientIdAndDocumentType(@Param("patientId") Long patientId,
            @Param("documentType") Document.DocumentType documentType);

    @Query("SELECT d FROM Document d WHERE d.booking.id = :bookingId AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByBookingId(@Param("bookingId") Long bookingId);

    @Query("SELECT d FROM Document d WHERE d.uploadDate BETWEEN :startDate AND :endDate AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByUploadDateBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT d FROM Document d WHERE d.documentType = :documentType AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByDocumentType(@Param("documentType") Document.DocumentType documentType);

    @Query("SELECT d FROM Document d WHERE d.uploadedByUsername = :username AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findByUploadedByUsername(@Param("username") String username);

    @Query("SELECT d FROM Document d WHERE d.storedFilename = :storedFilename AND d.isActive = true")
    Optional<Document> findByStoredFilename(@Param("storedFilename") String storedFilename);

    @Query("SELECT d FROM Document d WHERE d.filePath = :filePath AND d.isActive = true")
    Optional<Document> findByFilePath(@Param("filePath") String filePath);

    @Override
    Optional<Document> findById(Long id);

    @Override
    <S extends Document> S save(S entity);

    @Override
    void deleteById(Long id);

    // Soft delete - mark as inactive instead of physical delete
    @Query("UPDATE Document d SET d.isActive = false WHERE d.id = :id")
    void softDeleteById(@Param("id") Long id);

    // Count documents by type for analytics
    @Query("SELECT COUNT(d) FROM Document d WHERE d.documentType = :documentType AND d.isActive = true")
    Long countByDocumentType(@Param("documentType") Document.DocumentType documentType);

    // Find recent documents
    @Query("SELECT d FROM Document d WHERE d.uploadDate >= :since AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findRecentDocuments(@Param("since") LocalDateTime since);

    // Find large files (for cleanup/optimization)
    @Query("SELECT d FROM Document d WHERE d.fileSize > :sizeThreshold AND d.isActive = true ORDER BY d.fileSize DESC")
    List<Document> findLargeFiles(@Param("sizeThreshold") Long sizeThreshold);
}