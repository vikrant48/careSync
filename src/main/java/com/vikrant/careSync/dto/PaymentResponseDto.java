package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {
    
    private Long id;
    private String transactionId;
    private String paymentGatewayTransactionId;
    private BigDecimal amount;
    private String currency;
    private Payment.PaymentMethod paymentMethod;
    private Payment.PaymentStatus paymentStatus;
    private String description;
    private Long patientId;
    private String patientName;
    private Long bookingId;
    private LocalDateTime createdAt;
    private LocalDateTime paymentCompletedAt;
    private String failureReason;
    
    // Payment method specific details
    private String upiId;
    private String cardLastFour;
    private String cardType;
    
    // For payment initiation response
    private String paymentUrl;
    private String qrCodeData;
    private String razorpayOrderId;
    
    // Constructor for basic payment info
    public PaymentResponseDto(Payment payment) {
        this.id = payment.getId();
        this.transactionId = payment.getTransactionId();
        this.paymentGatewayTransactionId = payment.getPaymentGatewayTransactionId();
        this.amount = payment.getAmount();
        this.currency = payment.getCurrency();
        this.paymentMethod = payment.getPaymentMethod();
        this.paymentStatus = payment.getPaymentStatus();
        this.description = payment.getDescription();
        this.patientId = payment.getPatient().getId();
        this.patientName = payment.getPatient().getFirstName() + " " + payment.getPatient().getLastName();
        this.bookingId = payment.getBookingId();
        this.createdAt = payment.getCreatedAt();
        this.paymentCompletedAt = payment.getPaymentCompletedAt();
        this.failureReason = payment.getFailureReason();
        this.upiId = payment.getUpiId();
        this.cardLastFour = payment.getCardLastFour();
        this.cardType = payment.getCardType();
    }
}