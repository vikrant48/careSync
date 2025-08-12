package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.MedicalHistory;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalHistoryDto {
    private Long id;
    private LocalDate visitDate;
    private String symptoms;
    private String diagnosis;
    private String treatment;

    public MedicalHistoryDto(MedicalHistory medicalHistory) {
        this.id = medicalHistory.getId();
        this.visitDate = medicalHistory.getVisitDate();
        this.symptoms = medicalHistory.getSymptoms();
        this.diagnosis = medicalHistory.getDiagnosis();
        this.treatment = medicalHistory.getTreatment();
    }
}
