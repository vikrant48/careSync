package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientFinancialStatsDto implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Long totalBookings;
    private Long confirmedBookings;
    private Double totalSpend;
    private Double totalAppointmentSpend;
    private Double totalLabTestSpend;
    private Double averageSpentPerBooking;
}
