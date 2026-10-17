package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** The patient dashboard's "AI health brief": next appointment, how to prepare, pending items, unread lab reports */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientHealthBriefDto {

    /** Null when nothing is booked */
    private NextAppointment nextAppointment;
    /** 2-3 preparation tips for the next appointment (empty when nothing is booked) */
    private List<String> preparationTips;
    /** True when the tips were written by the AI, false for the standard checklist */
    private boolean tipsFromAi;
    private List<PendingItem> pendingFollowUps;
    private List<LabReportItem> unreadLabReports;
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextAppointment {
        private Long appointmentId;
        private String doctorName;
        private String specialization;
        private LocalDateTime dateTime;
        private String status;
        private boolean preVisitFormDone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingItem {
        /** PRE_VISIT_FORM or LAB_RESULTS_PENDING */
        private String type;
        private String text;
        private Long appointmentId;
        private Long bookingId;
        private LocalDateTime date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabReportItem {
        private Long documentId;
        private String filename;
        private LocalDateTime uploadedAt;
        private boolean explained;
    }
}
