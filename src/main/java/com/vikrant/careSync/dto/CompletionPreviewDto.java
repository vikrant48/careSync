package com.vikrant.careSync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompletionPreviewDto {
    private Long appointmentId;
    private String patientName;
    private Integer patientAge;
    private String patientGender;

    private PreVisitIntakeResponse intake;
    private SoapReportDto soapReport;
    private MedicalHistoryDto medicalHistory;

    @JsonProperty("isReadyForSign")
    private boolean isReadyForSign;

    @JsonProperty("readyForSign")
    public boolean getReadyForSign() {
        return isReadyForSign;
    }

    private List<String> readinessReasons;
}
