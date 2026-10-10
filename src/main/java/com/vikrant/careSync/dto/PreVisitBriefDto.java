package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreVisitBriefDto {
    private Long appointmentId;
    private Long patientId;
    private String historySummary;
    private String currentMedications;
    private String allergies;
    private String unfinishedFollowUps;
}
