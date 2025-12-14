package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "APIs for payment processing and management")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Pay for existing booking", description = "Process payment for a doctor-created booking")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request or booking not eligible"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/booking/{bookingId}/pay")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PaymentResponseDto> payForBooking(
            @Parameter(description = "Booking ID") @PathVariable Long bookingId,
            @Valid @RequestBody PaymentRequestDto request) {

        log.info("Payment request for booking ID: {}, patient ID: {}", bookingId, request.getPatientId());

        try {
            PaymentResponseDto response = paymentService.payForBooking(bookingId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Payment failed for booking ID: {}", bookingId, e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during payment for booking ID: {}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Initiate a new payment", description = "Creates a new payment transaction and returns payment details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment initiated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "404", description = "Patient or booking not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponseDto> initiatePayment(
            @Valid @RequestBody PaymentRequestDto request) {

        log.info("Payment initiation request received for patient ID: {}, amount: {}",
                request.getPatientId(), request.getAmount());

        try {
            PaymentResponseDto response = paymentService.initiatePayment(request);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Payment initiation failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get payment by transaction ID", description = "Retrieves payment details by transaction ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponseDto> getPaymentByTransactionId(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        try {
            PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
            return ResponseEntity.ok(payment);

        } catch (RuntimeException e) {
            log.error("Payment not found for transaction ID: {}", transactionId);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Update payment with booking ID", description = "Links a payment to a booking after booking creation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment updated successfully"),
            @ApiResponse(responseCode = "404", description = "Payment or booking not found")
    })
    @PutMapping("/link-booking")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<String> linkPaymentToBooking(
            @Parameter(description = "Transaction ID") @RequestParam String transactionId,
            @Parameter(description = "Booking ID") @RequestParam Long bookingId) {

        try {
            paymentService.updatePaymentWithBookingId(transactionId, bookingId);
            return ResponseEntity.ok("Payment linked to booking successfully");

        } catch (RuntimeException e) {
            log.error("Failed to link payment to booking: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get payment by booking ID", description = "Retrieves payment details by booking ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponseDto> getPaymentByBookingId(
            @Parameter(description = "Booking ID") @PathVariable Long bookingId) {

        try {
            PaymentResponseDto payment = paymentService.getPaymentByBookingId(bookingId);
            return ResponseEntity.ok(payment);

        } catch (RuntimeException e) {
            log.error("Payment not found for booking ID: {}", bookingId);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get payments by patient", description = "Retrieves all payments for a specific patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentsByPatient(
            @Parameter(description = "Patient ID") @PathVariable Long patientId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<PaymentResponseDto> payments = paymentService.getPaymentsByPatient(patientId, pageable);
            return ResponseEntity.ok(payments);

        } catch (RuntimeException e) {
            log.error("Error retrieving payments for patient ID: {}", patientId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get all payments", description = "Retrieves all payments with pagination (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<PaymentResponseDto>> getAllPayments(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PaymentResponseDto> payments = paymentService.getAllPayments(pageable);
        return ResponseEntity.ok(payments);
    }

    @Operation(summary = "Payment webhook callback", description = "Handles payment gateway webhook callbacks")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid webhook data"),
            @ApiResponse(responseCode = "401", description = "Invalid signature")
    })
    @PostMapping("/webhook/razorpay")
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        log.info("Razorpay webhook received");

        try {
            // Extract payment details from webhook payload
            Map<String, Object> paymentEntity = (Map<String, Object>) payload.get("payload");
            Map<String, Object> payment = (Map<String, Object>) paymentEntity.get("payment");
            Map<String, Object> entity = (Map<String, Object>) payment.get("entity");

            String razorpayPaymentId = (String) entity.get("id");
            String razorpayOrderId = (String) entity.get("order_id");

            // Process the callback
            paymentService.handlePaymentCallback(razorpayPaymentId, razorpayOrderId, signature);

            return ResponseEntity.ok(Map.of("status", "success"));

        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Verify payment status", description = "Manually verify payment status with gateway")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status verified"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/verify/{transactionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponseDto> verifyPaymentStatus(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        try {
            // This would typically involve checking with the payment gateway
            PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
            return ResponseEntity.ok(payment);

        } catch (RuntimeException e) {
            log.error("Payment verification failed for transaction ID: {}", transactionId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Cancel payment", description = "Cancel a pending payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be cancelled"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/cancel/{transactionId}")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cancelPayment(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        try {
            // Implementation would involve updating payment status to CANCELLED
            // and potentially calling payment gateway to cancel the order

            log.info("Payment cancellation requested for transaction ID: {}", transactionId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Payment cancellation initiated",
                    "transactionId", transactionId));

        } catch (RuntimeException e) {
            log.error("Payment cancellation failed for transaction ID: {}", transactionId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    @Operation(summary = "Get payment statistics", description = "Get payment statistics for dashboard (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPaymentStatistics() {

        try {
            // This would typically involve aggregating payment data
            // Implementation depends on specific requirements

            Map<String, Object> stats = Map.of(
                    "totalPayments", 0,
                    "successfulPayments", 0,
                    "failedPayments", 0,
                    "totalRevenue", 0.0,
                    "averageTransactionAmount", 0.0);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error retrieving payment statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception in PaymentController", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error", "message", e.getMessage()));
    }
}