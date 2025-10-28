package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    List<Booking> findByPatientIdOrderByBookingDateDesc(Long patientId);
    
    List<Booking> findByPatientOrderByBookingDateDesc(Patient patient);
    
    List<Booking> findByStatusOrderByBookingDateDesc(Booking.BookingStatus status);
    
    @Query("SELECT b FROM Booking b WHERE b.patient.id = :patientId AND b.status = :status ORDER BY b.bookingDate DESC")
    List<Booking> findByPatientIdAndStatus(@Param("patientId") Long patientId, @Param("status") Booking.BookingStatus status);
    
    @Query("SELECT b FROM Booking b WHERE b.bookingDate BETWEEN :startDate AND :endDate ORDER BY b.bookingDate DESC")
    List<Booking> findByBookingDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);
    
    @Query("SELECT b FROM Booking b JOIN FETCH b.selectedTests WHERE b.id = :bookingId")
    Booking findByIdWithTests(@Param("bookingId") Long bookingId);
}