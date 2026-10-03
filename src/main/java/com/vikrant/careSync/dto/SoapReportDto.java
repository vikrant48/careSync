package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoapReportDto {

    private Long id;
    private Long appointmentId;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private LocalDate visitDate;

    // Structured SOAP
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;

    // Standard clinical fields
    private String symptoms;
    private String diagnosis;
    private String treatment;
    private String medicine;
    private String doses;
    private String notes;
    private String transcript;

    // Status tracking
    private Boolean isDraft;
    private Boolean isSigned;
    private LocalDateTime signedAt;
}
