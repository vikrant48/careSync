package com.vikrant.careSync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClinicalDictationResponse {
    private boolean success;
    private String error;
    private String patientName;
    private String chiefComplaint;
    private String vitals;
    private String diagnosis;
    private List<PrescriptionItem> prescriptions;
    private List<String> labOrders;
    private String followUp;
    private SoapNote soapNote;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrescriptionItem {
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SoapNote {
        private String subjective;  // Patient history & reported symptoms
        private String objective;   // Physical findings & vitals
        private String assessment;  // Clinical diagnosis & evaluation
        private String plan;        // Treatment, medications & follow-up
    }
}
