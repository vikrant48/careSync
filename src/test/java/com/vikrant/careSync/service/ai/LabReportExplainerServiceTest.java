package com.vikrant.careSync.service.ai;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.security.access.AuthenticatedUser;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lab report explanations: made once on request, saved, shown with the note; only the own patient's reports. */
class LabReportExplainerServiceTest {

    private static final long PATIENT_ID = 801L;

    private DocumentRepository documents;
    private SupabaseStorageService storage;
    private AiClinicalService clinical;
    private CurrentUserService currentUser;
    private LabReportExplainerService explainer;
    private Document report;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        storage = mock(SupabaseStorageService.class);
        clinical = mock(AiClinicalService.class);
        currentUser = mock(CurrentUserService.class);
        explainer = new LabReportExplainerService(documents, storage, clinical, currentUser);

        report = Document.builder().id(55L).documentType(Document.DocumentType.LAB_REPORT).isActive(true)
                .contentType("application/pdf").filePath("private:lab_reports/test.pdf")
                .patient(Patient.builder().id(PATIENT_ID).build()).build();
        when(documents.findById(55L)).thenReturn(Optional.of(report));
        when(documents.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));
        loggedInAs(PATIENT_ID, User.Role.PATIENT);
    }

    private void loggedInAs(long id, User.Role role) {
        when(currentUser.current()).thenReturn(Optional.of(new AuthenticatedUser(id, "user.test", role)));
    }

    @Test
    void explainsOnceSavesItAndAddsTheNote() {
        when(storage.readOwnFile(eq(report.getFilePath()), eq(SupabaseStorageService.FileType.LAB_REPORT), anyLong()))
                .thenReturn(Optional.of(new byte[] { 1, 2, 3 }));
        when(clinical.explainLabReport(any(), eq("application/pdf"))).thenReturn("- Haemoglobin: within range");

        LabReportExplainerService.Explanation first = explainer.explain(55L);

        assertEquals("- Haemoglobin: within range", first.explanation());
        assertEquals(LabReportExplainerService.NOT_A_DIAGNOSIS, first.note());
        assertNotNull(report.getAiExplainedAt());
        assertNotNull(report.getPatientViewedAt(), "asking for an explanation counts as reading the report");

        explainer.explain(55L);
        verify(clinical, times(1)).explainLabReport(any(), any());
    }

    @Test
    void somethingThatIsNotALabReportIsNotSaved() {
        when(storage.readOwnFile(any(), any(), anyLong())).thenReturn(Optional.of(new byte[] { 1 }));
        when(clinical.explainLabReport(any(), any())).thenReturn(AiClinicalService.NOT_A_LAB_REPORT);

        assertThrows(IllegalArgumentException.class, () -> explainer.explain(55L));
        assertNull(report.getAiExplanation());
    }

    @Test
    void aiUnavailableSavesNothing() {
        when(storage.readOwnFile(any(), any(), anyLong())).thenReturn(Optional.of(new byte[] { 1 }));
        when(clinical.explainLabReport(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> explainer.explain(55L));
        assertNull(report.getAiExplanation());
    }

    @Test
    void onlyTheReportsOwnPatientCanAskForAnExplanation() {
        loggedInAs(901L, User.Role.DOCTOR);

        assertThrows(AccessDeniedException.class, () -> explainer.explain(55L));
        verifyNoInteractions(clinical, storage);
        assertNull(report.getPatientViewedAt(), "a doctor opening it does not mark it read for the patient");
    }

    @Test
    void otherDocumentTypesAreNotExplained() {
        report.setDocumentType(Document.DocumentType.PRESCRIPTION);
        assertThrows(IllegalArgumentException.class, () -> explainer.explain(55L));
        verifyNoInteractions(clinical);
    }

    @Test
    void openingTheReportMarksItReadOnlyOnce() {
        explainer.markSeenByPatient(55L);
        LocalDateTime firstSeen = report.getPatientViewedAt();
        assertNotNull(firstSeen);

        explainer.markSeenByPatient(55L);
        assertEquals(firstSeen, report.getPatientViewedAt());
        verify(documents, times(1)).save(report);
    }
}
