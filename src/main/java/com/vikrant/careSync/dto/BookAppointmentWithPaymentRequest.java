package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Payment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class BookAppointmentWithPaymentRequest {

    @NotNull(message = "Doctor ID is required")
    private Long doctorId;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "Appointment date and time must be in the future")
    private LocalDateTime appointmentDateTime;

    private String reason;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private Payment.PaymentMethod paymentMethod;

    private String upiId;

    private PaymentRequestDto.CardDetailsDto cardDetails;
}
