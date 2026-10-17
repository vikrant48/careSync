package com.vikrant.careSync.controller;

import com.vikrant.careSync.dto.AiChatRequest;
import com.vikrant.careSync.dto.AiChatResponse;
import com.vikrant.careSync.dto.MedicalSummaryResponse;
import com.vikrant.careSync.dto.DiagnosisRequest;
import com.vikrant.careSync.dto.DiagnosisSuggestionDto;
import com.vikrant.careSync.dto.VisionScanResponse;
import com.vikrant.careSync.dto.ClinicalDictationResponse;
import com.vikrant.careSync.dto.AiIntakeDraftRequest;
import com.vikrant.careSync.dto.AiIntakeDraftResponse;
import com.vikrant.careSync.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import com.vikrant.careSync.service.ai.AiQuotaExceededException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * Shared pool for streaming replies (it used to create a thread per request and never stop it). Small and
     * bounded: extra requests are refused instead of piling up. Each task runs as the user who started it.
     */
    private final ExecutorService streamExecutor = new DelegatingSecurityContextExecutorService(
            new ThreadPoolExecutor(4, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
                    runnable -> {
                        Thread thread = new Thread(runnable, "ai-stream");
                        thread.setDaemon(true);
                        return thread;
                    }));

    @PreDestroy
    void shutdownStreams() {
        streamExecutor.shutdownNow();
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.getResponse(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);
        try {
            streamExecutor.execute(() -> {
                try {
                    aiService.streamResponse(request, chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    });
                    emitter.complete();
                } catch (AiQuotaExceededException e) {
                    sendErrorAndClose(emitter, e.userMessage());
                } catch (Exception e) {
                    sendErrorAndClose(emitter, "The AI assistant is unavailable right now. Please try again.");
                }
            });
        } catch (RejectedExecutionException e) {
            sendErrorAndClose(emitter, "The AI assistant is busy right now. Please try again in a moment.");
        }
        return emitter;
    }

    /** Ends the stream with an "error" event carrying a message safe to show */
    private static void sendErrorAndClose(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.complete();
        }
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
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN') and @access.canAccessPatient(#patientId)")
    public ResponseEntity<MedicalSummaryResponse> summarize(@PathVariable Long patientId) {
        return ResponseEntity.ok(aiService.summarizePatientHistory(patientId));
    }

    @PostMapping("/suggest-diagnosis")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DiagnosisSuggestionDto> suggestDiagnosis(@RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(aiService.suggestDiagnosis(request.getSymptoms()));
    }

    @PostMapping(value = "/scan-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<VisionScanResponse> scanDocument(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(aiService.scanMedicalDocument(file));
    }

    @PostMapping("/dictate-note")
    @PreAuthorize("hasAnyRole('DOCTOR')")
    public ResponseEntity<ClinicalDictationResponse> dictateNote(
            @RequestBody Map<String, String> body) {
        String transcript = body != null ? body.get("transcript") : null;
        return ResponseEntity.ok(aiService.structureClinicalDictation(transcript));
    }

    @PostMapping("/draft-intake")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AiIntakeDraftResponse> draftIntake(
            @Valid @RequestBody AiIntakeDraftRequest request) {
        return ResponseEntity.ok(aiService.draftIntake(request.getNarrative()));
    }
}
