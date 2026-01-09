package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.DoctorLeave;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoctorLeaveDto {
    private Long id;
    private Long doctorId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    public DoctorLeaveDto(DoctorLeave leave) {
        this.id = leave.getId();
        this.doctorId = leave.getDoctor().getId();
        this.startDate = leave.getStartDate();
        this.endDate = leave.getEndDate();
        this.reason = leave.getReason();
    }
}
