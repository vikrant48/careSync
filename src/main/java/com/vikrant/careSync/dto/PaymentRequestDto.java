package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Payment;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.0", message = "Amount must be at least 1.0")
    @Digits(integer = 8, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;
    
    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
    
    @NotNull(message = "Payment method is required")
    private Payment.PaymentMethod paymentMethod;
    
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    private Long bookingId;
    
    @Size(max = 3, message = "Currency code cannot exceed 3 characters")
    private String currency = "INR";
    
    // UPI specific fields
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+$", 
             message = "Invalid UPI ID format")
    private String upiId;
    
    // Card specific fields
    private CardDetailsDto cardDetails;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetailsDto {
        
        @Pattern(regexp = "^[0-9]{13,19}$", message = "Invalid card number")
        private String cardNumber;
        
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month")
        private String expiryMonth;
        
        @Pattern(regexp = "^[0-9]{2}$", message = "Invalid expiry year")
        private String expiryYear;
        
        @Pattern(regexp = "^[0-9]{3,4}$", message = "Invalid CVV")
        private String cvv;
        
        @NotBlank(message = "Cardholder name is required")
        @Size(max = 100, message = "Cardholder name cannot exceed 100 characters")
        private String cardholderName;
    }
}