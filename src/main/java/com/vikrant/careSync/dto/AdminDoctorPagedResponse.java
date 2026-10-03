package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDoctorPagedResponse {
    private List<AdminDoctorListItemDto> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;
    private boolean empty;
    private long totalVerifiedCount;
    private long totalPendingCount;

    public static AdminDoctorPagedResponse fromPage(Page<AdminDoctorListItemDto> page, long totalVerifiedCount,
            long totalPendingCount) {
        return AdminDoctorPagedResponse.builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .size(page.getSize())
                .number(page.getNumber())
                .empty(page.isEmpty())
                .totalVerifiedCount(totalVerifiedCount)
                .totalPendingCount(totalPendingCount)
                .build();
    }
}
