package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisSuggestionDto {
    private List<ClinicalMatch> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClinicalMatch {
        private String diagnosis;
        private String treatment;
        private String medicine;
        private String dosage;
        private String reasoning;
    }
}
