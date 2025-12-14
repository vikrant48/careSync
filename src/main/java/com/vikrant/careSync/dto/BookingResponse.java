package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

    private Long id;
    private Long patientId;
    private String patientName;
    private List<LabTestDto> selectedTests;
    private BigDecimal totalPrice;
    private String prescribedBy;
    private LocalDateTime bookingDate;
    private Booking.BookingStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Payment related fields (optional)
    private String paymentTransactionId;
    private String paymentStatus;

    // Uploaded reports
    private List<DocumentDto> labReports;
}