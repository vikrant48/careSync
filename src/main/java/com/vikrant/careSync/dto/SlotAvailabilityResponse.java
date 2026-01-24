package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotAvailabilityResponse {
    private List<String> availableSlots;
    private boolean isOnLeave;
    private String leaveMessage;
    private LocalDate leaveEndDate;
}
