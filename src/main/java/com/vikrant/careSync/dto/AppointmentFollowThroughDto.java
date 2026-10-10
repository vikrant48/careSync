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
    private boolean feedbackRequested;
    private FollowUpSlotDto suggestedFollowUp;
    private FollowUpSlotDto replacementOffer;
}
