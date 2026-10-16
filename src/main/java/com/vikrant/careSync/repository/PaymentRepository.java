package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.entity.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Access-control lookup (AccessGuard): paying patient id only
    @Query("SELECT p.patient.id FROM Payment p WHERE p.transactionId = :transactionId")
    Optional<Long> findPatientIdByTransactionId(@Param("transactionId") String transactionId);

        // Find by transaction ID
        Optional<Payment> findByTransactionId(String transactionId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Payment p WHERE p.paymentGatewayTransactionId = :gatewayTransactionId")
        Optional<Payment> findByPaymentGatewayTransactionIdForUpdate(
                        @Param("gatewayTransactionId") String gatewayTransactionId);

        // Find payments by patient
        List<Payment> findByPatientOrderByCreatedAtDesc(Patient patient);

        // Find payments by patient with pagination
        Page<Payment> findByPatientOrderByCreatedAtDesc(Patient patient, Pageable pageable);

        // Find payments by booking ID
        @Query("SELECT p FROM Payment p WHERE p.bookingId = :bookingId")
        List<Payment> findByBookingId(@Param("bookingId") Long bookingId);

        // Find payments by appointment ID
        @Query("SELECT p FROM Payment p WHERE p.appointmentId = :appointmentId")
        List<Payment> findByAppointmentId(@Param("appointmentId") Long appointmentId);

        @Query("SELECT COUNT(p) FROM Payment p WHERE (p.bookingId = :bookingId OR p.appointmentId = :bookingId) AND p.paymentStatus IN ('PENDING', 'PROCESSING', 'SUCCESS')")
        long countActivePaymentsForBooking(@Param("bookingId") Long bookingId);

        // Booking and appointment ids come from different sequences, so they are counted separately
        @Query("SELECT COUNT(p) FROM Payment p WHERE p.bookingId = :bookingId AND p.paymentStatus IN ('PENDING', 'PROCESSING', 'SUCCESS')")
        long countActivePaymentsByBookingId(@Param("bookingId") Long bookingId);

        @Query("SELECT COUNT(p) FROM Payment p WHERE p.appointmentId = :appointmentId AND p.paymentStatus IN ('PENDING', 'PROCESSING', 'SUCCESS')")
        long countActivePaymentsByAppointmentId(@Param("appointmentId") Long appointmentId);

        // Get payment statistics by method
        @Query("SELECT p.paymentMethod, COUNT(p), SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'SUCCESS' AND p.createdAt BETWEEN :startDate AND :endDate GROUP BY p.paymentMethod")
        List<Object[]> getPaymentStatisticsByMethod(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // Get patient financial stats aggregated by payment type
        @Query("SELECT p.paymentType, SUM(p.amount) FROM Payment p WHERE p.patient.id = :patientId AND p.paymentStatus = 'SUCCESS' GROUP BY p.paymentType")
        List<Object[]> getPatientPaymentStats(@Param("patientId") Long patientId);

}
