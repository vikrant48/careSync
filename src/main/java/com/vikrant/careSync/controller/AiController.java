package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.getResponse(request));
    }

    @GetMapping("/summarize/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<MedicalSummaryResponse> summarize(@PathVariable Long patientId) {
        return ResponseEntity.ok(aiService.summarizePatientHistory(patientId));
    }
}
