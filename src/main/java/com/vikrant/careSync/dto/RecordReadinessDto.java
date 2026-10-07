package com.vikrant.careSync.dto;

import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordReadinessDto {
    private Long appointmentId;
    private boolean isReady;
    private boolean recordSaved;
    private boolean soapSaved;

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
