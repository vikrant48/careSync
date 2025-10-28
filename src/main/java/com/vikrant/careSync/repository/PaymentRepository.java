package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    // Find by transaction ID
    Optional<Payment> findByTransactionId(String transactionId);
    
    // Find by payment gateway transaction ID
    Optional<Payment> findByPaymentGatewayTransactionId(String gatewayTransactionId);
    
    // Find payments by patient
    List<Payment> findByPatientOrderByCreatedAtDesc(Patient patient);
    
    // Find payments by patient with pagination
    Page<Payment> findByPatientOrderByCreatedAtDesc(Patient patient, Pageable pageable);
    
    // Find payments by status
    List<Payment> findByPaymentStatus(Payment.PaymentStatus status);
    
    // Find payments by method
    List<Payment> findByPaymentMethod(Payment.PaymentMethod method);
    
    // Find payments by booking ID
    @Query("SELECT p FROM Payment p WHERE p.bookingId = :bookingId")
    List<Payment> findByBookingId(@Param("bookingId") Long bookingId);
    
    // Find successful payments by patient
    @Query("SELECT p FROM Payment p WHERE p.patient = :patient AND p.paymentStatus = 'SUCCESS' ORDER BY p.createdAt DESC")
    List<Payment> findSuccessfulPaymentsByPatient(@Param("patient") Patient patient);
    
    // Find payments within date range
    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    List<Payment> findPaymentsBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    // Get total revenue by date range
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentStatus = 'SUCCESS' AND p.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalRevenueByDateRange(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
    
    // Get payment statistics by method
    @Query("SELECT p.paymentMethod, COUNT(p), SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'SUCCESS' AND p.createdAt BETWEEN :startDate AND :endDate GROUP BY p.paymentMethod")
    List<Object[]> getPaymentStatisticsByMethod(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    // Find failed payments for retry
    @Query("SELECT p FROM Payment p WHERE p.paymentStatus IN ('FAILED', 'CANCELLED') AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Payment> findFailedPaymentsSince(@Param("since") LocalDateTime since);
    
    // Count payments by status for dashboard
    @Query("SELECT p.paymentStatus, COUNT(p) FROM Payment p GROUP BY p.paymentStatus")
    List<Object[]> getPaymentCountByStatus();
    
    // Find pending payments older than specified time
    @Query("SELECT p FROM Payment p WHERE p.paymentStatus IN ('PENDING', 'PROCESSING') AND p.createdAt < :cutoffTime")
    List<Payment> findStalePayments(@Param("cutoffTime") LocalDateTime cutoffTime);
}