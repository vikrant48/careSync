package com.vikrant.careSync.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DoctorDashboardMetricsResponse {
    private long todayAppointments;
    private long appointmentChangeFromYesterday;
    private long totalPatients;
    private Long newPatientsThisMonth;
    private Double patientChangePercent;
    private long pendingReports;
    private long pendingReportChangeFromYesterday;
    private Double satisfactionRating;
    private long reviewCount;
    private Double satisfactionChangeThisMonth;
}
