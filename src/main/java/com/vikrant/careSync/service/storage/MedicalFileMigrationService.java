package com.vikrant.careSync.service.storage;

import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.repository.DocumentRepository;
import com.vikrant.careSync.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off move of medical files that were uploaded to the public bucket into the private bucket.
 * Each file is copied, its record is pointed at the private copy, and only then (if asked) the public copy is
 * deleted. A file that fails is reported and left as it was; the run can be repeated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalFileMigrationService {

    private final DocumentRepository documentRepository;
    private final SupabaseStorageService storageService;

    @CacheEvict(value = { "PATIENT:DOCUMENTS", "DOCTOR:DOCUMENTS", "PATIENT:COMPLETE_DATA" }, allEntries = true)
    public Map<String, Object> moveMedicalFilesToPrivateBucket(boolean dryRun, boolean deleteOriginals) {
        if (!storageService.isPrivateBucketConfigured()) {
            throw new IllegalStateException("Set SUPABASE_S3_PRIVATE_BUCKET before moving files");
        }
        List<Document> candidates = documentRepository.findAll().stream()
                .filter(doc -> Boolean.TRUE.equals(doc.getIsActive()))
                .filter(doc -> doc.getDocumentType() != null
                        && SupabaseStorageService.FileType.valueOf(doc.getDocumentType().name()).isMedical())
                .filter(doc -> doc.getFilePath() != null && !storageService.isPrivate(doc.getFilePath()))
                .toList();

        int moved = 0;
        int originalsDeleted = 0;
        List<Long> failedDocumentIds = new ArrayList<>();
        if (!dryRun) {
            for (Document doc : candidates) {
                String publicPath = doc.getFilePath();
                try {
                    String privatePath = storageService.copyToPrivate(publicPath);
                    doc.setFilePath(privatePath);
                    doc.setStoredFilename(storageService.extractKey(privatePath));
                    documentRepository.save(doc);
                    moved++;
                    if (deleteOriginals && storageService.deleteFile(publicPath)) {
                        originalsDeleted++;
                    }
                } catch (Exception e) {
                    log.error("Could not move document {} to the private bucket", doc.getId(), e);
                    failedDocumentIds.add(doc.getId());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("medicalFilesInPublicBucket", candidates.size());
        result.put("moved", moved);
        result.put("publicCopiesDeleted", originalsDeleted);
        result.put("failedDocumentIds", failedDocumentIds);
        log.info("Medical file move: {}", result);
        return result;
    }
}
