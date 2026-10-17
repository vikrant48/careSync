package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Booking;
import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.AppointmentRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import com.vikrant.careSync.util.ErrorLog;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final RazorpayService razorpayService;
    private final AppointmentRepository appointmentRepository;
    private final PaymentPreferenceService paymentPreferenceService;
    private final AfterCommitTaskDispatcher afterCommitTaskDispatcher;
    private final Semaphore paymentGatewaySemaphore = new Semaphore(10, true);

    static final String DEMO_GATEWAY_NOTE = "DEMO MODE: no payment gateway was charged and no money was collected";

    private static final Pattern UPI_ID = Pattern.compile("^[a-zA-Z0-9._%+-]{2,256}@[a-zA-Z0-9.-]{2,64}$");
    private static final Pattern CARD_NUMBER = Pattern.compile("^[0-9]{13,19}$");

    // Visits that were cancelled, rejected or closed without taking place cannot be paid for
    private static final EnumSet<Appointment.Status> UNPAYABLE_APPOINTMENT_STATUSES = EnumSet.of(
            Appointment.Status.CANCELLED, Appointment.Status.CANCELLED_BY_PATIENT,
            Appointment.Status.CANCELLED_BY_DOCTOR, Appointment.Status.REJECTED, Appointment.Status.NO_SHOW,
            Appointment.Status.NO_SHOW_PATIENT, Appointment.Status.NO_SHOW_DOCTOR, Appointment.Status.AUTO_CLOSED);

    @Value("${app.payment.demo-mode:true}")
    private boolean demoMode;

    @Value("${app.payment.currency:INR}")
    private String paymentCurrency;

    public boolean isDemoMode() {
        return demoMode;
    }

    /**
     * Pay for an existing doctor-created booking
     */
    public PaymentResponseDto payForBooking(Long bookingId, PaymentRequestDto paymentRequest) {
        log.info("Processing payment for booking ID: {}", bookingId);
        // This endpoint pays for the booking in the path only. initiatePayment runs every check (demo mode,
        // payment details, patient owns the booking, PENDING status, exact amount, no active payment) and
        // locks the booking row for this transaction.
        paymentRequest.setBookingId(bookingId);
        paymentRequest.setAppointmentId(null);
        PaymentResponseDto response = initiatePayment(paymentRequest);

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));
        booking.setStatus(Booking.BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        log.info("Successfully processed payment for booking ID: {}, transaction ID: {}",
                bookingId, response.getTransactionId());
        return response;
    }

    /**
     * Initiate a new payment
     */
    public PaymentResponseDto initiatePayment(PaymentRequestDto request) {
        log.info("Initiating payment for patient ID: {}, amount: {}", request.getPatientId(), request.getAmount());
        requireDemoMode();
        validatePaymentDetails(request);
        requireCurrency(request);

        // Validate patient exists
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));

        // The amount must be the server's price for this patient's own appointment or booking
        Booking booking = validatePaymentTarget(request, patient);

        // Create payment record
        Payment payment = createPaymentRecord(request, patient, booking);
        // Payment is already saved in createPaymentRecord method

        PaymentResponseDto response;
        try {
            response = chargeInDemoMode(payment, request);

            log.info("Payment initiated successfully. Transaction ID: {}", payment.getTransactionId());

        } catch (Exception e) {
            ErrorLog.error(e, "Payment initiation failed for transaction: {}", payment.getTransactionId());
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
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(),
                payment.getTransactionId());

        payment.setPaymentGatewayTransactionId(razorpayOrderId);
        payment.setUpiId(request.getUpiId().trim());
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        // Don't save here - will be saved in the calling method

        PaymentResponseDto response = new PaymentResponseDto(payment);
        response.setRazorpayOrderId(razorpayOrderId);
        response.setPaymentUrl(
                razorpayService.getUpiPaymentUrlWithAmount(razorpayOrderId, request.getUpiId(), payment.getAmount()));

        return response;
    }

    /**
     * Process card payment
     */
    private PaymentResponseDto processCardPayment(Payment payment, PaymentRequestDto request) {
        // Create Razorpay order for card payment
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(),
                payment.getTransactionId());

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
        String razorpayOrderId = razorpayService.createOrder(payment.getAmount(), payment.getCurrency(),
                payment.getTransactionId());

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
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentGatewayTransactionIdForUpdate(razorpayOrderId);
        if (paymentOpt.isEmpty()) {
            log.error("Payment not found for order: {}", razorpayOrderId);
            throw new RuntimeException("Payment not found");
        }

        Payment payment = paymentOpt.get();
        if (payment.getPaymentStatus() == Payment.PaymentStatus.SUCCESS) {
            log.info("Ignoring duplicate successful callback for transaction: {}", payment.getTransactionId());
            return;
        }

        try {
            // Get payment details from Razorpay
            var paymentDetails = callPaymentGateway(() -> razorpayService.getPaymentDetails(razorpayPaymentId));

            if ("captured".equals(paymentDetails.get("status"))) {
                payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
                payment.setPaymentCompletedAt(LocalDateTime.now());
                payment.setGatewayResponse(paymentDetails.toString());

                if (payment.getBookingId() != null) {
                    Booking booking = bookingRepository.findByIdForUpdate(payment.getBookingId())
                            .orElseThrow(() -> new RuntimeException(
                                    "Booking not found with ID: " + payment.getBookingId()));
                    if (booking.getStatus() == Booking.BookingStatus.PENDING) {
                        booking.setStatus(Booking.BookingStatus.COMPLETED);
                        bookingRepository.save(booking);
                    }
                }

                log.info("Payment successful. Transaction ID: {}", payment.getTransactionId());
            } else {
                payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason("Payment not captured: " + paymentDetails.get("status"));

                log.warn("Payment failed. Transaction ID: {}, Status: {}",
                        payment.getTransactionId(), paymentDetails.get("status"));
            }

        } catch (Exception e) {
            ErrorLog.error(e, "Error processing payment callback for transaction: {}", payment.getTransactionId());
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
        return getPaymentFor(bookingId, null);
    }

    /**
     * Booking and appointment ids are separate sequences, so "id 5" can mean either. type BOOKING or APPOINTMENT
     * looks in one place only; without a type, bookings are checked first (old callers), never an OR of both.
     */
    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentFor(Long id, String type) {
        List<Payment> payments = new java.util.ArrayList<>();
        if (type == null || "BOOKING".equalsIgnoreCase(type)) {
            payments = paymentRepository.findByBookingId(id);
        }
        if (payments.isEmpty() && (type == null || "APPOINTMENT".equalsIgnoreCase(type))) {
            payments = paymentRepository.findByAppointmentId(id);
        }
        if (!payments.isEmpty()) {
            return new PaymentResponseDto(payments.get(0));
        }

        throw new RuntimeException("Payment not found for booking ID: " + id);
    }

    @Transactional(readOnly = true)
    public boolean hasPaymentsForAppointment(Long appointmentId) {
        return !paymentRepository.findByAppointmentId(appointmentId).isEmpty();
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
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        Page<Payment> payments = paymentRepository.findAll(pageable);
        return payments.map(PaymentResponseDto::new);
    }

    /**
     * Create payment record
     */
    private Payment createPaymentRecord(PaymentRequestDto request, Patient patient, Booking booking) {
        Payment payment = new Payment();
        payment.setTransactionId(generateTransactionId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(paymentCurrency);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        payment.setPaymentType(request.getPaymentType());
        payment.setPatient(patient);
        payment.setRefundStatus(Payment.RefundStatus.NOT_REFUNDED);

        // Auto-generate description if not provided
        String description = request.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = request.getPaymentType().generateDescription(request.getAdditionalInfo());
        }
        payment.setDescription(description);

        // Set booking_id to the booking's ID if booking exists, or request's bookingId
        if (booking != null) {
            payment.setBookingId(booking.getId());
        } else if (request.getBookingId() != null) {
            payment.setBookingId(request.getBookingId());
        } else {
            payment.setBookingId(null);
        }

        if (request.getAppointmentId() != null) {
            payment.setAppointmentId(request.getAppointmentId());
        }

        // Save payment with the correct booking_id
        payment = paymentRepository.save(payment);

        return payment;
    }

    private PaymentResponseDto processPaymentThroughGateway(Payment payment, PaymentRequestDto request) {
        return callPaymentGateway(() -> switch (request.getPaymentMethod()) {
            case UPI -> processUpiPayment(payment, request);
            case CARD -> processCardPayment(payment, request);
            case QR_CODE -> processQrPayment(payment, request);
            default -> throw new RuntimeException("Unsupported payment method: " + request.getPaymentMethod());
        });
    }

    private boolean hasActivePaymentForBooking(Long bookingId) {
        return paymentRepository.countActivePaymentsByBookingId(bookingId) > 0;
    }

    private void requireDemoMode() {
        // Only the demo flow exists; there is no live gateway integration that could collect money
        if (!demoMode) {
            throw new IllegalStateException("Online payments are unavailable: no live payment gateway is configured.");
        }
    }

    private void requireCurrency(PaymentRequestDto request) {
        if (request.getCurrency() != null && !paymentCurrency.equalsIgnoreCase(request.getCurrency())) {
            throw new RuntimeException("Only " + paymentCurrency + " payments are supported");
        }
    }

    private static void validatePaymentDetails(PaymentRequestDto request) {
        if (request.getAmount() == null || request.getAmount().signum() < 0) {
            throw new RuntimeException("A valid payment amount is required");
        }
        if (request.getPaymentMethod() == null) {
            throw new RuntimeException("Payment method is required");
        }
        switch (request.getPaymentMethod()) {
            case UPI -> {
                if (request.getUpiId() == null || !UPI_ID.matcher(request.getUpiId().trim()).matches()) {
                    throw new RuntimeException("A valid UPI ID is required for UPI payments");
                }
            }
            case CARD -> {
                PaymentRequestDto.CardDetailsDto card = request.getCardDetails();
                if (card == null || card.getCardNumber() == null || !CARD_NUMBER.matcher(card.getCardNumber()).matches()) {
                    throw new RuntimeException("Valid card details are required for card payments");
                }
            }
            case QR_CODE -> {
                // No payer details needed
            }
            default -> throw new RuntimeException("Unsupported payment method: " + request.getPaymentMethod());
        }
    }

    /**
     * A payment must pay for this patient's own appointment or lab booking, at the price the server knows.
     * Returns the booking when the payment is for one.
     */
    private Booking validatePaymentTarget(PaymentRequestDto request, Patient patient) {
        Long bookingId = request.getBookingId();
        Long appointmentId = request.getAppointmentId();
        if (bookingId != null && appointmentId != null) {
            throw new RuntimeException("A payment can be for an appointment or a lab booking, not both");
        }

        if (bookingId != null) {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));
            if (!booking.getPatient().getId().equals(patient.getId())) {
                throw new RuntimeException("Booking does not belong to this patient");
            }
            if (booking.getStatus() != Booking.BookingStatus.PENDING) {
                throw new RuntimeException("Booking is not in PENDING status. Current status: " + booking.getStatus());
            }
            if (request.getAmount().compareTo(booking.getTotalPrice()) != 0) {
                throw new RuntimeException("Payment amount does not match booking total");
            }
            if (hasActivePaymentForBooking(bookingId)) {
                throw new RuntimeException("An active payment already exists for this booking");
            }
            return booking;
        }

        if (appointmentId != null) {
            Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
            if (!appointment.getPatient().getId().equals(patient.getId())) {
                throw new RuntimeException("Appointment does not belong to this patient");
            }
            if (UNPAYABLE_APPOINTMENT_STATUSES.contains(appointment.getStatus())) {
                throw new RuntimeException("This appointment can no longer be paid for. Current status: "
                        + appointment.getStatus());
            }
            // A doctor without a fee set sees free consultations
            BigDecimal fee = appointment.getDoctor().getConsultationFees() != null
                    ? appointment.getDoctor().getConsultationFees()
                    : BigDecimal.ZERO;
            if (request.getAmount().compareTo(fee) != 0) {
                throw new RuntimeException("Payment amount does not match the doctor's consultation fee of "
                        + fee.toPlainString() + " " + paymentCurrency);
            }
            if (paymentRepository.countActivePaymentsByAppointmentId(appointmentId) > 0) {
                throw new RuntimeException("An active payment already exists for this appointment");
            }
            return null;
        }

        throw new RuntimeException("A payment must be linked to an appointment or a lab booking");
    }

    /**
     * Demo mode: the gateway is a mock, so the payment is marked paid at once and labelled as a demo.
     * The full card number and CVV are dropped from the request whatever the outcome.
     */
    private PaymentResponseDto chargeInDemoMode(Payment payment, PaymentRequestDto request) {
        try {
            PaymentResponseDto response = processPaymentThroughGateway(payment, request);

            payment.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            payment.setPaymentCompletedAt(LocalDateTime.now());
            payment.setGatewayResponse(DEMO_GATEWAY_NOTE);
            paymentRepository.save(payment);

            response.setPaymentStatus(Payment.PaymentStatus.SUCCESS);
            response.setPaymentCompletedAt(payment.getPaymentCompletedAt());
            response.setDemoMode(true);

            rememberPaymentMethod(payment, request);
            return response;
        } finally {
            discardSensitiveCardData(request);
        }
    }

    /** Saves the method and its safe details once the payment has committed; a failure here never fails the payment. */
    private void rememberPaymentMethod(Payment payment, PaymentRequestDto request) {
        Payment.PaymentMethod method = payment.getPaymentMethod();
        String cardholderName = method == Payment.PaymentMethod.CARD && request.getCardDetails() != null
                ? request.getCardDetails().getCardholderName()
                : null;
        PaymentPreferenceService.SavedDetails details = new PaymentPreferenceService.SavedDetails(method,
                method == Payment.PaymentMethod.UPI ? request.getUpiId() : null,
                method == Payment.PaymentMethod.CARD ? payment.getCardLastFour() : null,
                method == Payment.PaymentMethod.CARD ? payment.getCardType() : null,
                cardholderName);
        Long patientId = payment.getPatient().getId();
        afterCommitTaskDispatcher.submitAfterCommit("save payment method for patient " + patientId,
                () -> paymentPreferenceService.remember(patientId, details));
    }

    private static void discardSensitiveCardData(PaymentRequestDto request) {
        PaymentRequestDto.CardDetailsDto card = request.getCardDetails();
        if (card != null) {
            card.setCardNumber(null);
            card.setCvv(null);
            card.setExpiryMonth(null);
            card.setExpiryYear(null);
        }
    }

    private <T> T callPaymentGateway(Callable<T> gatewayCall) {
        boolean permitAcquired = false;
        try {
            permitAcquired = paymentGatewaySemaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (!permitAcquired) {
                throw new RuntimeException("Payment gateway is busy. Please try again shortly.");
            }
            return gatewayCall.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            ErrorLog.debug(exception);
            throw new RuntimeException("Payment gateway call failed: " + exception.getMessage(), exception);
        } finally {
            if (permitAcquired) {
                paymentGatewaySemaphore.release();
            }
        }
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
