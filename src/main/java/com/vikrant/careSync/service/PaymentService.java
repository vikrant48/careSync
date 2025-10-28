package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.BookingRepository;
import com.vikrant.careSync.repository.PaymentRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final RazorpayService razorpayService;
    
    @Value("${app.payment.transaction-timeout-minutes:30}")
    private int transactionTimeoutMinutes;
    
    /**
     * Pay for an existing doctor-created booking
     */
    public PaymentResponseDto payForBooking(Long bookingId, PaymentRequestDto paymentRequest) {
        log.info("Processing payment for booking ID: {}", bookingId);
        
        // Validate booking exists and is in PENDING status
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));
        
        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Booking is not in PENDING status. Current status: " + booking.getStatus());
        }
        
        // Validate patient exists
        Patient patient = patientRepository.findById(paymentRequest.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + paymentRequest.getPatientId()));
        
        // Validate that the patient is the same as the booking's patient
        if (!booking.getPatient().getId().equals(patient.getId())) {
            throw new RuntimeException("Patient ID does not match the booking's patient");
        }
        
        // Validate payment amount matches booking total
        if (paymentRequest.getAmount().compareTo(booking.getTotalPrice()) != 0) {
            throw new RuntimeException("Payment amount does not match booking total");
        }
        
        // Create payment record directly for this booking (avoid duplicate creation)
        Payment payment = createPaymentRecord(paymentRequest, patient, booking);
        
        try {
            // Process payment based on method
            PaymentResponseDto response = new PaymentResponseDto(payment);
            
            switch (paymentRequest.getPaymentMethod()) {
                case UPI:
                    response = processUpiPayment(payment, paymentRequest);
                    break;
                case CARD:
                    response = processCardPayment(payment, paymentRequest);
                    break;
                case QR_CODE:
                    response = processQrPayment(payment, paymentRequest);
                    break;
                default:
                    throw new RuntimeException("Unsupported payment method: " + paymentRequest.getPaymentMethod());
            }
            
            // For demo purposes, mark payment as successful immediately
            // In production, this would be handled by webhook callbacks
            payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            payment.setPaymentCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Update booking status to COMPLETED
            booking.setStatus(Booking.BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            
            // Update response to reflect successful status
            response.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            response.setPaymentCompletedAt(LocalDateTime.now());
            
            log.info("Successfully processed payment for booking ID: {}, transaction ID: {}", 
                    bookingId, payment.getTransactionId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Payment failed for booking ID: {}", bookingId, e);
            payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    /**
     * Initiate a new payment
     */
    public PaymentResponseDto initiatePayment(PaymentRequestDto request) {
        log.info("Initiating payment for patient ID: {}, amount: {}", request.getPatientId(), request.getAmount());
        
        // Validate patient exists
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));
        
        // Validate booking if provided
        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + request.getBookingId()));
        }
        
        // Create payment record
        Payment payment = createPaymentRecord(request, patient, booking);
        // Payment is already saved in createPaymentRecord method
        
        // Process payment based on method
        PaymentResponseDto response = new PaymentResponseDto(payment);
        
        try {
            switch (request.getPaymentMethod()) {
                case UPI:
                    response = processUpiPayment(payment, request);
                    break;
                case CARD:
                    response = processCardPayment(payment, request);
                    break;
                case QR_CODE:
                    response = processQrPayment(payment, request);
                    break;
                default:
                    throw new RuntimeException("Unsupported payment method: " + request.getPaymentMethod());
            }
            
            // For demo purposes, mark payment as successful immediately
            // In production, this would be handled by webhook callbacks
            payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            payment.setPaymentCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Update response to reflect successful status
            response.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            response.setPaymentCompletedAt(LocalDateTime.now());
            
            log.info("Payment initiated successfully. Transaction ID: {}", payment.getTransactionId());
            
        } catch (Exception e) {
            log.error("Payment initiation failed for transaction: {}", payment.getTransactionId(), e);
            payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            throw new RuntimeException("Payment initiation failed: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * Process UPI payment
     */
    private PaymentResponseDto processUpiPayment(Payment payment, PaymentRequestDto request) {
        // Create Razorpay order for UPI
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(), payment.getTransactionId());
        
        payment.setPaymentGatewayTransactionId(razorpayOrderId);
        payment.setUpiId(request.getUpiId());
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        // Don't save here - will be saved in the calling method
        
        PaymentResponseDto response = new PaymentResponseDto(payment);
        response.setRazorpayOrderId(razorpayOrderId);
        response.setPaymentUrl(razorpayService.getUpiPaymentUrlWithAmount(razorpayOrderId, request.getUpiId(), payment.getAmount()));
        
        return response;
    }
    
    /**
     * Process card payment
     */
    private PaymentResponseDto processCardPayment(Payment payment, PaymentRequestDto request) {
        // Create Razorpay order for card payment
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(), payment.getTransactionId());
        
        payment.setPaymentGatewayTransactionId(razorpayOrderId);
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        
        // Store masked card details
        if (request.getCardDetails() != null) {
            String cardNumber = request.getCardDetails().getCardNumber();
            payment.setCardLastFour(cardNumber.substring(cardNumber.length() - 4));
            payment.setCardType(determineCardType(cardNumber));
        }
        
        // Don't save here - will be saved in the calling method
        
        PaymentResponseDto response = new PaymentResponseDto(payment);
        response.setRazorpayOrderId(razorpayOrderId);
        response.setPaymentUrl(razorpayService.getCardPaymentUrl(razorpayOrderId));
        
        return response;
    }
    
    /**
     * Process QR code payment
     */
    private PaymentResponseDto processQrPayment(Payment payment, PaymentRequestDto request) {
        // Create Razorpay order for QR payment
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(), payment.getTransactionId());
        
        payment.setPaymentGatewayTransactionId(razorpayOrderId);
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        // Don't save here - will be saved in the calling method
        
        PaymentResponseDto response = new PaymentResponseDto(payment);
        response.setRazorpayOrderId(razorpayOrderId);
        response.setQrCodeData(razorpayService.generateQrCodeWithAmount(razorpayOrderId, payment.getAmount()));
        
        return response;
    }
    
    /**
     * Handle payment webhook callback
     */
    public void handlePaymentCallback(String razorpayPaymentId, String razorpayOrderId, String razorpaySignature) {
        log.info("Processing payment callback for order: {}", razorpayOrderId);
        
        // Verify signature
        if (!razorpayService.verifySignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            log.error("Invalid signature for payment callback. Order: {}", razorpayOrderId);
            throw new RuntimeException("Invalid payment signature");
        }
        
        // Find payment by gateway transaction ID
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentGatewayTransactionId(razorpayOrderId);
        if (paymentOpt.isEmpty()) {
            log.error("Payment not found for order: {}", razorpayOrderId);
            throw new RuntimeException("Payment not found");
        }
        
        Payment payment = paymentOpt.get();
        
        try {
            // Get payment details from Razorpay
            var paymentDetails = razorpayService.getPaymentDetails(razorpayPaymentId);
            
            if ("captured".equals(paymentDetails.get("status"))) {
                payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
                payment.setPaymentCompletedAt(LocalDateTime.now());
                payment.setGatewayResponse(paymentDetails.toString());
                
                log.info("Payment successful. Transaction ID: {}", payment.getTransactionId());
            } else {
                payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason("Payment not captured: " + paymentDetails.get("status"));
                
                log.warn("Payment failed. Transaction ID: {}, Status: {}", 
                        payment.getTransactionId(), paymentDetails.get("status"));
            }
            
        } catch (Exception e) {
            log.error("Error processing payment callback for transaction: {}", payment.getTransactionId(), e);
            payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("Callback processing error: " + e.getMessage());
        }
        
        paymentRepository.save(payment);
    }
    
    /**
     * Get payment by transaction ID
     */
    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found with transaction ID: " + transactionId));
        
        return new PaymentResponseDto(payment);
    }
    
    /**
     * Get payment by booking ID
     */
    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentByBookingId(Long bookingId) {
        // Find payment by booking_id field
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        if (payments.isEmpty()) {
            throw new RuntimeException("Payment not found for booking ID: " + bookingId);
        }
        
        // Return the first payment (there should only be one per booking)
        return new PaymentResponseDto(payments.get(0));
    }
    
    /**
     * Get payments by patient
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponseDto> getPaymentsByPatient(Long patientId, Pageable pageable) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
        
        Page<Payment> payments = paymentRepository.findByPatientOrderByCreatedAtDesc(patient, pageable);
        return payments.map(PaymentResponseDto::new);
    }
    
    /**
     * Get all payments with pagination
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponseDto> getAllPayments(Pageable pageable) {
        Page<Payment> payments = paymentRepository.findAll(pageable);
        return payments.map(PaymentResponseDto::new);
    }
    
    /**
     * Update payment with booking ID (deprecated - booking_id is now auto-set to payment id)
     */
    @Transactional
    public void updatePaymentWithBookingId(String transactionId, Long bookingId) {
        // This method is now deprecated since booking_id is automatically set to payment id
        // But keeping it for backward compatibility
        log.info("Payment linking is now automatic. Transaction ID: {}, Booking ID: {}", transactionId, bookingId);
    }
    
    /**
     * Clean up stale payments
     */
    @Transactional
    public void cleanupStalePayments() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(transactionTimeoutMinutes);
        List<Payment> stalePayments = paymentRepository.findStalePayments(cutoffTime);
        
        for (Payment payment : stalePayments) {
            payment.setPaymentStatus(Payment.PaymentStatus.CANCELLED);
            payment.setFailureReason("Transaction timeout");
            log.info("Cancelled stale payment: {}", payment.getTransactionId());
        }
        
        if (!stalePayments.isEmpty()) {
            paymentRepository.saveAll(stalePayments);
            log.info("Cleaned up {} stale payments", stalePayments.size());
        }
    }
    
    /**
     * Create payment record
     */
    private Payment createPaymentRecord(PaymentRequestDto request, Patient patient, Booking booking) {
        Payment payment = new Payment();
        payment.setTransactionId(generateTransactionId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        payment.setDescription(request.getDescription());
        payment.setPatient(patient);
        payment.setRefundStatus(Payment.RefundStatus.NOT_REFUNDED);
        
        // Set booking_id to the booking's ID if booking exists, otherwise null
        if (booking != null) {
            payment.setBookingId(booking.getId());
        } else {
            payment.setBookingId(null);
        }
        
        // Save payment with the correct booking_id
        payment = paymentRepository.save(payment);
        
        return payment;
    }
    
    /**
     * Generate unique transaction ID
     */
    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Determine card type from card number
     */
    private String determineCardType(String cardNumber) {
        if (cardNumber.startsWith("4")) {
            return "VISA";
        } else if (cardNumber.startsWith("5") || cardNumber.startsWith("2")) {
            return "MASTERCARD";
        } else if (cardNumber.startsWith("3")) {
            return "AMEX";
        } else {
            return "UNKNOWN";
        }
    }
}