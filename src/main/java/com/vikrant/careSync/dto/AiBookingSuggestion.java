package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiBookingSuggestion {
    public enum SuggestionType {
        SPECIALIZATIONS, DOCTORS, DATES, SLOTS, CONFIRM, MY_APPOINTMENTS
    }

    private SuggestionType type;
    private List<String> specializations;
    private List<DoctorSuggestion> doctors;
    private List<AppointmentDto> appointments;
    private List<String> slots;

    // For single selection / confirmation
    private Long doctorId;
    private String doctorName;
    private String specialization;
    private String slot;
    private String date; // YYYY-MM-DD
    private String originalDate; // For rescheduling: original date
    private String originalSlot; // For rescheduling: original slot
    private Long appointmentId; // For rescheduling: original appointment ID
    private BigDecimal consultationFee;
    private String reason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorSuggestion {
        private Long id;
        private String name;
        private String specialization;
        private BigDecimal consultationFee;
        private String profileImageUrl;
        private String languages;
        private Integer experience; // Total years of service
        private Boolean isOnLeave;
        private String leaveMessage;
        private Boolean isVerified;
    }
}
