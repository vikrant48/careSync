package com.vikrant.careSync.controller.support;

import com.vikrant.careSync.dto.UserDto;
import com.vikrant.careSync.entity.Document;
import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.service.DocumentService;
import com.vikrant.careSync.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.vikrant.careSync.util.ErrorLog;

/**
 * The shared part of every file-upload endpoint: who is uploading, and the same three error answers.
 * Replaces twelve copies in PatientController and FileUploadController.
 */
@Component
@RequiredArgsConstructor
public class DocumentUploads {

    private final UserService userService;
    private final DocumentService documentService;

    /** One upload: receives the uploader's username and role, returns the response body. */
    @FunctionalInterface
    public interface Upload {
        Map<String, Object> run(String username, String userType) throws Exception;
    }

    /**
     * @param failLabel    what failed to upload, e.g. "lab report" (storage errors)
     * @param invalidLabel prefix for a rejected file, e.g. "Invalid file" (validation errors)
     */
    public ResponseEntity<?> handle(Authentication authentication, String failLabel, String invalidLabel,
            Upload upload) {
        try {
            UserDto userDto = userService.getUserByUsername(authentication.getName());
            String userType = userDto != null ? userDto.getRole() : "UNKNOWN";
            return ResponseEntity.ok(upload.run(authentication.getName(), userType));
        } catch (IOException e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body("Failed to upload " + failLabel + ": " + ClientErrorMessages.of(e));
        } catch (IllegalArgumentException e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(invalidLabel + ": " + ClientErrorMessages.of(e));
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body("Error: " + ClientErrorMessages.of(e));
        }
    }

    /** id, filename, url, downloadUrl, fileUrl, size, uploadDate (signed links for private files). */
    public Map<String, Object> standardResponse(Document document) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", document.getId());
        response.put("filename", document.getOriginalFilename());
        response.put("url", documentService.viewUrl(document));
        response.put("downloadUrl", documentService.downloadUrl(document));
        response.put("fileUrl", documentService.viewUrl(document));
        response.put("size", document.getFileSize());
        response.put("uploadDate", document.getUploadDate());
        return response;
    }
}
