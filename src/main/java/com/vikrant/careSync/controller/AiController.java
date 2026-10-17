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
import java.util.concurrent.atomic.AtomicBoolean;
import com.vikrant.careSync.service.ai.agent.AiAgentService;
import com.vikrant.careSync.service.ai.agent.DoctorCopilotService;
import com.vikrant.careSync.service.ai.LabReportExplainerService;
import com.vikrant.careSync.service.ai.PatientHealthBriefService;
import com.vikrant.careSync.dto.AiSymptomSearchRequest;
import com.vikrant.careSync.dto.PatientHealthBriefDto;
import org.springframework.http.HttpStatus;
import java.util.List;
import com.vikrant.careSync.util.ErrorLog;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final DoctorCopilotService doctorCopilotService;
    private final LabReportExplainerService labReportExplainerService;
    private final PatientHealthBriefService patientHealthBriefService;

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

    /**
     * Streaming chat. Server-sent events:
     * "status" {"text"} while a tool runs, "delta" {"text"} pieces of the answer, then "done" with the full
     * AiChatResponse (text, card, emergency notice), or "error" with a message safe to show. Closing the connection
     * (the Stop button) stops the agent before its next step.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(90000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        try {
            streamExecutor.execute(() -> {
                try {
                    AiChatResponse response = aiService.getResponse(request, new AiAgentService.AgentListener() {
                        @Override
                        public void onText(String text) {
                            send(emitter, closed, "delta", Map.of("text", text));
                        }

                        @Override
                        public void onStatus(String status) {
                            send(emitter, closed, "status", Map.of("text", status));
                        }

                        @Override
                        public boolean cancelled() {
                            return closed.get();
                        }
                    });
                    send(emitter, closed, "done", response);
                    emitter.complete();
                } catch (AiQuotaExceededException e) {
                    ErrorLog.log(e);
                    sendErrorAndClose(emitter, e.userMessage());
                } catch (Exception e) {
                    ErrorLog.log(e);
                    sendErrorAndClose(emitter, "The AI assistant is unavailable right now. Please try again.");
                }
            });
        } catch (RejectedExecutionException e) {
            ErrorLog.log(e);
            sendErrorAndClose(emitter, "The AI assistant is busy right now. Please try again in a moment.");
        }
        return emitter;
    }

    /** Sends one JSON event; a failed send means the client went away (Stop or closed tab) */
    private static void send(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            ErrorLog.log(e);
            closed.set(true);
        }
    }

    /** Ends the stream with an "error" event carrying a message safe to show */
    private static void sendErrorAndClose(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception ignored) {
            ErrorLog.log(ignored);
            emitter.complete();
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public SseEmitter streamChatGet(@RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) Long patientId) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        request.setConversationId(conversationId);
        request.setPatientId(patientId);
        return streamChat(request);
    }

    /** Find & Book "Describe your problem": a suggested specialization and doctor cards (or an emergency notice) */
    @PostMapping("/symptom-search")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AiChatResponse> symptomSearch(@Valid @RequestBody AiSymptomSearchRequest request) {
        return ResponseEntity.ok(aiService.symptomSearch(request.getDescription()));
    }

    /** Patient dashboard "AI health brief" */
    @GetMapping("/patient/health-brief")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientHealthBriefDto> healthBrief() {
        return ResponseEntity.ok(patientHealthBriefService.brief());
    }

    /** The saved plain-language explanation of a lab report (204 when none was made yet) */
    @GetMapping("/lab-reports/{documentId}/explanation")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR') and @access.canAccessDocument(#documentId)")
    public ResponseEntity<?> labReportExplanation(@PathVariable Long documentId) {
        try {
            return labReportExplainerService.saved(documentId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            ErrorLog.log(e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /** Explains a lab report in plain language (the patient's own report), saves it, and returns it */
    @PostMapping("/lab-reports/{documentId}/explain")
    @PreAuthorize("hasRole('PATIENT') and @access.canAccessDocument(#documentId)")
    public ResponseEntity<?> explainLabReport(@PathVariable Long documentId) {
        try {
            return ResponseEntity.ok(labReportExplainerService.explain(documentId));
        } catch (AiQuotaExceededException e) {
            ErrorLog.log(e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", e.userMessage()));
        } catch (IllegalArgumentException e) {
            ErrorLog.log(e);
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            ErrorLog.log(e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        }
    }

    /** Doctor co-pilot: the doctor's own patients for the @patient picker (id, name, last appointment; no contacts) */
    @GetMapping("/doctor/patients")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<DoctorCopilotService.PatientOption>> copilotPatients() {
        return ResponseEntity.ok(doctorCopilotService.myPatients());
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
