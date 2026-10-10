package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpSlotDto {
    private Long doctorId;
    private String doctorName;
    private String specialization;
    private String date;
    private String slot;
    private BigDecimal consultationFee;
    private String reason;
}
