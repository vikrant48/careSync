package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.projection.BookingOwners;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Access-control lookups (AccessGuard): owner ids only
    @Query("SELECT new com.vikrant.careSync.repository.projection.BookingOwners(p.id, d.id) FROM Booking b JOIN b.patient p LEFT JOIN b.doctor d WHERE b.id = :id")
    Optional<BookingOwners> findOwnersById(@Param("id") Long id);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.patient p LEFT JOIN FETCH p.user LEFT JOIN FETCH b.selectedTests ORDER BY b.bookingDate DESC")
    List<Booking> findAllWithDetails();

    List<Booking> findByPatientIdOrderByBookingDateDesc(Long patientId);

    // Health brief: the patient's lab test bookings still waiting for results, with their tests
    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.selectedTests WHERE b.patient.id = :patientId"
            + " AND b.status = 'PENDING' ORDER BY b.bookingDate DESC")
    List<Booking> findPendingWithTestsByPatientId(@Param("patientId") Long patientId);

    List<Booking> findByPatientOrderByBookingDateDesc(Patient patient);

    List<Booking> findByStatusOrderByBookingDateDesc(Booking.BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.patient.id = :patientId AND b.status = :status ORDER BY b.bookingDate DESC")
    List<Booking> findByPatientIdAndStatus(@Param("patientId") Long patientId,
            @Param("status") Booking.BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.bookingDate BETWEEN :startDate AND :endDate ORDER BY b.bookingDate DESC")
    List<Booking> findByBookingDateBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b JOIN FETCH b.patient WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);
}
