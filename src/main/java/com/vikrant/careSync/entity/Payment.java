package com.vikrant.careSync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;
    
    @Column(name = "payment_gateway_transaction_id")
    private String paymentGatewayTransactionId;
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false)
    private String currency = "INR";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;
    
    @Column(name = "description")
    private String description;
    
    // UPI specific fields
    @Column(name = "upi_id")
    private String upiId;
    
    @Column(name = "upi_transaction_ref")
    private String upiTransactionRef;
    
    // Card specific fields
    @Column(name = "card_last_four")
    private String cardLastFour;
    
    @Column(name = "card_type")
    private String cardType;
    
    @Column(name = "card_network")
    private String cardNetwork;
    
    // Booking ID - same as payment ID for simplified linking
    // This is NOT a foreign key, just a reference field
    @Column(name = "booking_id", nullable = true)
    private Long bookingId;
    
    // Patient reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;
    
    // Payment gateway response
    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;
    
    @Column(name = "refund_status")
    @Enumerated(EnumType.STRING)
    private RefundStatus refundStatus;
    
    @Column(name = "refund_transaction_id")
    private String refundTransactionId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "payment_completed_at")
    private LocalDateTime paymentCompletedAt;
    
    // Enums
    public enum PaymentMethod {
        UPI, CARD, QR_CODE, NET_BANKING, WALLET
    }
    
    public enum PaymentStatus {
        PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED, REFUNDED, PARTIAL_REFUND
    }
    
    public enum RefundStatus {
        NOT_REFUNDED, REFUND_PENDING, REFUND_PROCESSING, REFUNDED, REFUND_FAILED
    }
    
    // Helper methods
    public boolean isSuccessful() {
        return PaymentStatus.SUCCESS.equals(this.paymentStatus);
    }
    
    public boolean isPending() {
        return PaymentStatus.PENDING.equals(this.paymentStatus) || 
               PaymentStatus.PROCESSING.equals(this.paymentStatus);
    }
    
    public boolean isFailed() {
        return PaymentStatus.FAILED.equals(this.paymentStatus) || 
               PaymentStatus.CANCELLED.equals(this.paymentStatus);
    }
}