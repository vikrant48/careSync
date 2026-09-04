package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeedBulkResponse {
    private String status;
    private String message;
    private int createdCount;
    private Long startUserId;
    private Long lastUserId;
    private String defaultPasswordUsed;
    private long executionTimeMs;
}
