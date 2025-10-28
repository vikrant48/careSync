package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientBookingWithPaymentRequest {
    
    private BookingRequest bookingRequest;
    private PaymentRequestDto paymentRequest;
}