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
    private String preVisitSummary;
    private boolean intakeCompleted;
    private LocalDateTime updatedAt;
}
