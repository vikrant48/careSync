package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.repository.projection.DocumentOwners;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Access-control lookup (AccessGuard): owner ids only
    @Query("SELECT new com.vikrant.careSync.repository.projection.DocumentOwners(p.id, dr.id) FROM Document d LEFT JOIN d.patient p LEFT JOIN d.doctor dr WHERE d.id = :id")
    Optional<DocumentOwners> findOwnersById(@Param("id") Long id);

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

    @Override
    Optional<Document> findById(Long id);

    @Override
    <S extends Document> S save(S entity);

    @Override
    void deleteById(Long id);

    // Find recent documents
    @Query("SELECT d FROM Document d WHERE d.uploadDate >= :since AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findRecentDocuments(@Param("since") LocalDateTime since);

    /** Active documents of many bookings at once (booking lists). */
    @Query("SELECT d FROM Document d WHERE d.booking.id IN :bookingIds AND d.isActive = true ORDER BY d.uploadDate DESC")
    List<Document> findActiveByBookingIdIn(@Param("bookingIds") java.util.Collection<Long> bookingIds);
}
