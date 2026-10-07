package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreVisitIntakeResponse {

    private Long appointmentId;
    private String chiefComplaint;
    private String symptoms;
    private String symptomsSummary;
    private String symptomDuration;
    private String duration;
    private String severity;
    private String currentMedications;
    private String allergies;
    private Boolean consentGiven;
    private String preVisitSummary;
    private String aiTriageSummary;
    private boolean intakeCompleted;
    private String intakeStatus;
    private Boolean isConfirmedByPatient;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private Boolean hasRedFlags;
    private String redFlags;
    private String editHistory;
    private LocalDateTime updatedAt;

    public String getAiTriageSummary() {
        return aiTriageSummary != null ? aiTriageSummary : preVisitSummary;
    }

    public String getSymptomsSummary() {
        return symptomsSummary != null ? symptomsSummary : symptoms;
    }

    public String getDuration() {
        return duration != null ? duration : symptomDuration;
    }
}
