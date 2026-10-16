package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.PaymentPreferenceDto;
import com.vikrant.careSync.entity.PatientPaymentPreference;
import com.vikrant.careSync.entity.Payment;
import com.vikrant.careSync.repository.PatientPaymentPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Remembers the payment method each patient used last. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentPreferenceService {

    private static final int MAX_CARDHOLDER_NAME_LENGTH = 100;

    private final PatientPaymentPreferenceRepository preferenceRepository;

    /**
     * The details that are safe to keep. The full card number, expiry date and CVV have no place here,
     * and a card number that is longer than four digits is refused.
     */
    public record SavedDetails(Payment.PaymentMethod paymentMethod, String upiId, String cardLastFour,
            String cardBrand, String cardholderName) {

        public SavedDetails {
            if (paymentMethod == null) {
                throw new IllegalArgumentException("Payment method is required");
            }
            if (cardLastFour != null && !cardLastFour.matches("\\d{4}")) {
                throw new IllegalArgumentException("Only the last four card digits may be saved");
            }
            upiId = blankToNull(upiId);
            cardBrand = blankToNull(cardBrand);
            cardholderName = blankToNull(cardholderName);
            if (cardholderName != null && cardholderName.length() > MAX_CARDHOLDER_NAME_LENGTH) {
                cardholderName = cardholderName.substring(0, MAX_CARDHOLDER_NAME_LENGTH);
            }
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** Replaces the saved method; details of the previous method are cleared. */
    @Transactional
    public void remember(Long patientId, SavedDetails details) {
        PatientPaymentPreference preference = preferenceRepository.findByPatientId(patientId)
                .orElseGet(() -> {
                    PatientPaymentPreference created = new PatientPaymentPreference();
                    created.setPatientId(patientId);
                    return created;
                });
        preference.setPaymentMethod(details.paymentMethod());
        preference.setUpiId(details.upiId());
        preference.setCardLastFour(details.cardLastFour());
        preference.setCardBrand(details.cardBrand());
        preference.setCardholderName(details.cardholderName());
        preferenceRepository.save(preference);
        log.info("Saved last used payment method for patient ID: {}", patientId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentPreferenceDto> find(Long patientId) {
        return preferenceRepository.findByPatientId(patientId).map(preference -> PaymentPreferenceDto.builder()
                .paymentMethod(preference.getPaymentMethod())
                .upiId(preference.getUpiId())
                .cardLastFour(preference.getCardLastFour())
                .cardBrand(preference.getCardBrand())
                .cardholderName(preference.getCardholderName())
                .updatedAt(preference.getUpdatedAt())
                .build());
    }

    @Transactional
    public void forget(Long patientId) {
        preferenceRepository.deleteByPatientId(patientId);
    }
}
