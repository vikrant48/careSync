package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** The patient's last used payment method. Card payments only ever expose the last four digits. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPreferenceDto {
    private Payment.PaymentMethod paymentMethod;
    private String upiId;
    private String cardLastFour;
    private String cardBrand;
    private String cardholderName;
    private LocalDateTime updatedAt;
}
