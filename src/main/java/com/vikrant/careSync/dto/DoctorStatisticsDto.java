package com.vikrant.careSync.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorStatisticsDto {
    private Long doctorId;
    private String doctorName;
    private String specialization;
    private Long totalAppointments;
    private Long completedAppointments;
    private Long cancelledAppointments;
    private Double averageRating;
    private Long totalPatients;
    private Double completionRate;
}
