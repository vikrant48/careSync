package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.dto.DiagnosisRequest;
import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.getResponse(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                aiService.streamResponse(request, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public SseEmitter streamChatGet(@RequestParam String message,
            @RequestParam(required = false) String conversationId) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        request.setConversationId(conversationId);
        return streamChat(request);
    }

    @GetMapping("/summarize/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<MedicalSummaryResponse> summarize(@PathVariable Long patientId) {
        return ResponseEntity.ok(aiService.summarizePatientHistory(patientId));
    }

    @PostMapping("/suggest-diagnosis")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DiagnosisSuggestionDto> suggestDiagnosis(@RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(aiService.suggestDiagnosis(request.getSymptoms()));
    }
}
