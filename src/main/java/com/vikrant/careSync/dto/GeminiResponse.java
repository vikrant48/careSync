package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiResponse {
    private List<Candidate> candidates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private Content content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    public String getFirstText() {
        if (candidates != null && !candidates.isEmpty() &&
                candidates.get(0).getContent() != null &&
                candidates.get(0).getContent().getParts() != null &&
                !candidates.get(0).getContent().getParts().isEmpty()) {
            return candidates.get(0).getContent().getParts().get(0).getText();
        }
        return null;
    }
}
