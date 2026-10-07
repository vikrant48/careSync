package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllowedActionDto {
    private String actionKey;
    private String label;
    private String icon;
    private boolean enabled;
    private String disabledReason;
    private int stageIndex;
    private boolean isPrimary;
}
