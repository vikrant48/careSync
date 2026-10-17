package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Emergency or urgent-care notice shown above the chat answer: what to do now, numbers to call (tap to call) and,
 * for medical cases, a shortcut to emergency booking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiEmergencyInfo {

    /** EMERGENCY (act now) or URGENT (see a doctor today) */
    private String level;
    /** MEDICAL or MENTAL_HEALTH */
    private String category;
    private String title;
    private String message;
    /** Why it was flagged (short, e.g. "chest pain") */
    private List<String> reasons;
    private List<Contact> contacts;
    /** Offer the emergency booking shortcut (medical cases) */
    private boolean emergencyBooking;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Contact {
        private String label;
        private String number;
    }
}
