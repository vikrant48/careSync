package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionScanResponse {
    private boolean success;
    private String error;
    private String documentType; // PRESCRIPTION, LAB_REPORT, MEDICAL_NOTE, UNKNOWN
    private String patientName;
    private String doctorName;
    private String date;
    private List<MedicationItem> medications;
    private List<LabResultItem> labResults;
    private String rawSummary;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationItem {
        private String name;
        private String dosage;
        private String frequency;
        private String duration;
        private String instructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabResultItem {
        private String testName;
        private String resultValue;
        private String referenceRange;
        private String status; // NORMAL, HIGH, LOW, ABNORMAL
    }
}
