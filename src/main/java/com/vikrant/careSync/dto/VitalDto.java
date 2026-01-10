package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Vital;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VitalDto {
    private Long id;
    private Long patientId;
    private Double systolicBP;
    private Double diastolicBP;
    private Double sugarLevel;
    private Double weight;
    private Double temperature;
    private Double heartRate;
    private Double respiratoryRate;
    private LocalDateTime recordedAt;

    public VitalDto(Vital vital) {
        this.id = vital.getId();
        this.patientId = vital.getPatient().getId();
        this.systolicBP = vital.getSystolicBP();
        this.diastolicBP = vital.getDiastolicBP();
        this.sugarLevel = vital.getSugarLevel();
        this.weight = vital.getWeight();
        this.temperature = vital.getTemperature();
        this.heartRate = vital.getHeartRate();
        this.respiratoryRate = vital.getRespiratoryRate();
        this.recordedAt = vital.getRecordedAt();
    }
}
