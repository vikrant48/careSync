package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIntakeDraftResponse {
    private String chiefComplaint;
    private List<String> symptoms;
    private String duration;
    private String severity;
    private String currentMedications;
    private String allergies;
    private List<String> redFlags;
    private boolean hasEmergencyFlags;
    private String emergencyMessage;
}
