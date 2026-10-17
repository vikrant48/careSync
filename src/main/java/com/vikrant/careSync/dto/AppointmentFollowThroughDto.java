package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentFollowThroughDto {
    private String plainLanguageSummary;
    /** When the saved summary was written; null when it is not saved (AI unavailable: recorded facts shown) */
    private java.time.LocalDateTime plainLanguageSummarySavedAt;
    private boolean feedbackRequested;
    private FollowUpSlotDto suggestedFollowUp;
    private FollowUpSlotDto replacementOffer;
}
