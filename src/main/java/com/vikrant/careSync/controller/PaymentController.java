package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.dto.PaymentPreferenceDto;
import com.vikrant.careSync.dto.PaymentRequestDto;
import com.vikrant.careSync.dto.PaymentResponseDto;
import com.vikrant.careSync.exception.GlobalExceptionHandler;
import com.vikrant.careSync.security.access.AccessGuard;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.PaymentPreferenceService;
import com.vikrant.careSync.service.PaymentService;
import org.springframework.security.access.AccessDeniedException;
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
import com.vikrant.careSync.util.ErrorLog;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "APIs for payment processing and management")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class PaymentController {

    private final PaymentService paymentService;
    private final AccessGuard accessGuard;
    private final PaymentPreferenceService paymentPreferenceService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Pay for existing booking", description = "Process payment for a doctor-created booking")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request or booking not eligible"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/booking/{bookingId}/pay")
    @PreAuthorize("hasRole('PATIENT') and @access.isSelf(#request.patientId)")
    public ResponseEntity<PaymentResponseDto> payForBooking(
            @Parameter(description = "Booking ID") @PathVariable Long bookingId,
            @Valid @RequestBody PaymentRequestDto request) {

        log.info("Payment request for booking ID: {}, patient ID: {}", bookingId, request.getPatientId());

        try {
            PaymentResponseDto response = paymentService.payForBooking(bookingId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            ErrorLog.error(e, "Payment failed for booking ID: {}", bookingId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            ErrorLog.error(e, "Unexpected error during payment for booking ID: {}", bookingId);
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
    @PreAuthorize("(hasRole('PATIENT') and @access.isSelf(#request.patientId)) or hasRole('ADMIN')")
    public ResponseEntity<PaymentResponseDto> initiatePayment(
            @Valid @RequestBody PaymentRequestDto request) {

        log.info("Payment initiation request received for patient ID: {}, amount: {}",
                request.getPatientId(), request.getAmount());

        try {
            PaymentResponseDto response = paymentService.initiatePayment(request);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            ErrorLog.error(e, "Payment initiation failed");
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get payment by transaction ID", description = "Retrieves payment details by transaction ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("@access.canAccessPaymentTransaction(#transactionId)")
    public ResponseEntity<PaymentResponseDto> getPaymentByTransactionId(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        try {
            PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
            return ResponseEntity.ok(payment);

        } catch (RuntimeException e) {
            ErrorLog.error(e, "Payment not found for transaction ID: {}", transactionId);
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
            @Parameter(description = "Booking ID") @PathVariable Long bookingId,
            @Parameter(description = "BOOKING or APPOINTMENT; omitted = booking first, then appointment")
            @RequestParam(required = false) String type) {

        PaymentResponseDto payment;
        try {
            payment = paymentService.getPaymentFor(bookingId, type);
        } catch (RuntimeException e) {
            ErrorLog.error(e, "Payment not found for booking ID: {}", bookingId);
            return ResponseEntity.notFound().build();
        }
        // The id may match a booking or an appointment, so check ownership on the payment found
        accessGuard.requirePatientAccess(payment.getPatientId());
        return ResponseEntity.ok(payment);
    }

    @Operation(summary = "Get payments by patient", description = "Retrieves all payments for a specific patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@access.canAccessPatient(#patientId)")
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
            ErrorLog.error(e, "Error retrieving payments for patient ID: {}", patientId);
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

        // In demo mode nothing is charged, so there is no gateway event to accept.
        // Before going live this needs Razorpay's webhook check (HMAC of the raw body with the webhook secret).
        if (paymentService.isDemoMode()) {
            log.warn("Razorpay webhook ignored: payments run in demo mode");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Payment webhooks are disabled in demo mode"));
        }

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
            ErrorLog.error(e, "Webhook processing failed");
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ClientErrorMessages.of(e)));
        }
    }

    @Operation(summary = "Verify payment status", description = "Manually verify payment status with gateway")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status verified"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/verify/{transactionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR') and @access.canAccessPaymentTransaction(#transactionId)")
    public ResponseEntity<PaymentResponseDto> verifyPaymentStatus(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        try {
            // This would typically involve checking with the payment gateway
            PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
            return ResponseEntity.ok(payment);

        } catch (RuntimeException e) {
            ErrorLog.error(e, "Payment verification failed for transaction ID: {}", transactionId);
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
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN') and @access.canAccessPaymentTransaction(#transactionId)")
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
            ErrorLog.error(e, "Payment cancellation failed for transaction ID: {}", transactionId);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", ClientErrorMessages.of(e)));
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
            ErrorLog.error(e, "Error retrieving payment statistics");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get my saved payment method", description = "The payment method the patient used last, with safe details only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved payment method"),
            @ApiResponse(responseCode = "204", description = "No payment method saved yet")
    })
    @GetMapping("/preferences/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PaymentPreferenceDto> getMyPaymentPreference() {
        Long patientId = currentUserService.requirePatient().getId();
        return paymentPreferenceService.find(patientId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Forget my saved payment method", description = "Deletes the saved payment method of the patient")
    @ApiResponse(responseCode = "204", description = "Saved payment method removed")
    @DeleteMapping("/preferences/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> forgetMyPaymentPreference() {
        paymentPreferenceService.forget(currentUserService.requirePatient().getId());
        return ResponseEntity.noContent().build();
    }

    // Must be declared here: the local Exception handler below would otherwise turn 403 into 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(GlobalExceptionHandler.forbiddenBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception in PaymentController", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error", "message", ClientErrorMessages.of(e)));
    }
}