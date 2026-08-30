package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroqAiStructuredResponse {
    private String reply;
    private String intent;
    private List<String> specializations;
    private String action;
    private String doctorName;
    private String date;
    private String slot;
    private String reason;
}
