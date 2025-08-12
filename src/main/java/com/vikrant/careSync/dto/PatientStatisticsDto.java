package com.vikrant.careSync.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientStatisticsDto {
    private Long patientId;
    private String patientName;
    private Long totalAppointments;
    private Long completedAppointments;
    private Long cancelledAppointments;
    private Long totalMedicalHistoryEntries;
    private String lastVisitDate;
    private Double averageRatingGiven;
}
