package com.vikrant.careSync.entity;

import com.vikrant.careSync.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * The payment method a patient used last, so the payment popup can offer it again.
 * Only safe details are kept: never the full card number, expiry date or CVV.
 */
@Entity
@Table(name = "patient_payment_preferences")
@Getter
@Setter
@NoArgsConstructor
public class PatientPaymentPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false, unique = true)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private Payment.PaymentMethod paymentMethod;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "upi_id", length = 512)
    private String upiId;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "cardholder_name", length = 512)
    private String cardholderName;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
