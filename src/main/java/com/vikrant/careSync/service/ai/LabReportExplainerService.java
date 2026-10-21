package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import com.vikrant.careSync.util.ErrorLog;

/**
 * Plain-language explanations of lab reports. Made only when the patient asks (one AI call per report), then
 * saved encrypted with the report and shown straight away next time. Always shown with a "not a diagnosis" note.
 * Callers check access to the document first (@access.canAccessDocument).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LabReportExplainerService {

    public static final String NOT_A_DIAGNOSIS = "This explanation is not a diagnosis. Only your doctor can "
            + "interpret these results together with your symptoms and health history.";
    public static final String NOT_A_LAB_REPORT_NOTE = "This file is not a lab report, so it was not explained. "
            + "Upload the lab report itself, or ask your doctor about this file.";
    static final long MAX_FILE_BYTES = 8_000_000L;
    private static final Set<String> READABLE_TYPES = Set.of("application/pdf", "image/png", "image/jpeg",
            "image/jpg", "image/webp");

    private final DocumentRepository documentRepository;
    private final SupabaseStorageService storageService;
    private final AiClinicalService clinicalService;
    private final CurrentUserService currentUserService;

    /** A saved explanation, with the note that must be shown with it */
    public record Explanation(Long documentId, String explanation, String note, LocalDateTime explainedAt) {
    }

    /** The saved explanation, if one was made; opening it counts as reading the report */
    public Optional<Explanation> saved(Long documentId) {
        Document document = labReport(documentId);
        markSeenByPatient(document);
        return document.getAiExplanation() == null ? Optional.empty() : Optional.of(toExplanation(document));
    }

    /**
     * The explanation of a lab report: the saved one, or a new one made now and saved. Only the report's own
     * patient can ask for one (it uses their AI allowance).
     *
     * When the file is not a lab report, the AI's own description of it is returned (not saved).
     *
     * @throws IllegalArgumentException the file type can't be read (only PDF or images)
     * @throws IllegalStateException    the file or the AI is unavailable right now (nothing saved; try again)
     */
    public Explanation explain(Long documentId) {
        Document document = labReport(documentId);
        if (!isOwnPatient(document)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        markSeenByPatient(document);
        if (document.getAiExplanation() != null) {
            return toExplanation(document);
        }
        String mimeType = document.getContentType() == null ? "" : document.getContentType().toLowerCase();
        if (!READABLE_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Only PDF or image lab reports can be explained.");
        }
        byte[] file = storageService.readOwnFile(document.getFilePath(), SupabaseStorageService.FileType.LAB_REPORT,
                MAX_FILE_BYTES).orElseThrow(() -> new IllegalStateException(
                        "This report could not be read right now. Please try again later."));

        // No database connection is held while the AI reads the file (it can take a while)
        String answer = clinicalService.explainLabReport(file, "image/jpg".equals(mimeType) ? "image/jpeg" : mimeType);
        if (answer == null) {
            throw new IllegalStateException("The explanation is unavailable right now. Please try again later.");
        }
        if (answer.contains(AiClinicalService.NOT_A_LAB_REPORT)) {
            // Show what the AI says the file is; not saved, so a corrected upload can still be explained
            String said = answer.replace(AiClinicalService.NOT_A_LAB_REPORT, "").replaceFirst("^[\\s:.\\-]+", "").trim();
            return new Explanation(documentId,
                    said.isBlank() ? "This file doesn't look like a lab report, so it can't be explained." : said,
                    NOT_A_LAB_REPORT_NOTE, null);
        }
        Document fresh = documentRepository.findById(documentId).orElseThrow(this::notFound);
        if (fresh.getAiExplanation() == null) {
            fresh.setAiExplanation(answer);
            fresh.setAiExplainedAt(LocalDateTime.now());
            fresh = documentRepository.save(fresh);
        }
        return toExplanation(fresh);
    }

    /** The report's own patient opened it: it no longer counts as unread. Anyone else opening it changes nothing. */
    public void markSeenByPatient(Long documentId) {
        documentRepository.findById(documentId).ifPresent(this::markSeenByPatient);
    }

    private void markSeenByPatient(Document document) {
        if (document.getDocumentType() != Document.DocumentType.LAB_REPORT || document.getPatientViewedAt() != null
                || !isOwnPatient(document)) {
            return;
        }
        try {
            document.setPatientViewedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (Exception e) {
            // Never block opening a report because the "seen" time could not be saved
            ErrorLog.warn(e, "Could not mark a lab report as seen");
        }
    }

    private boolean isOwnPatient(Document document) {
        Optional<AuthenticatedUser> user = currentUserService.current();
        return user.isPresent() && user.get().isPatient() && document.getPatient() != null
                && user.get().id().equals(document.getPatient().getId());
    }

    private Document labReport(Long documentId) {
        Document document = documentRepository.findById(documentId).orElseThrow(this::notFound);
        if (document.getDocumentType() != Document.DocumentType.LAB_REPORT
                || !Boolean.TRUE.equals(document.getIsActive()) || document.getPatient() == null) {
            throw notFound();
        }
        return document;
    }

    private IllegalArgumentException notFound() {
        return new IllegalArgumentException("Lab report not found.");
    }

    private static Explanation toExplanation(Document document) {
        return new Explanation(document.getId(), document.getAiExplanation(), NOT_A_DIAGNOSIS,
                document.getAiExplainedAt());
    }
}
