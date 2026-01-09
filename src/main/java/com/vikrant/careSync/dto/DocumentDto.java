package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Document;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private Long id;
    private String filename;
    private String url;
    private String downloadUrl;
    private String fileUrl;
    private Long size;
    private LocalDateTime uploadDate;
    private String description;
    private String contentType;
    private String documentType;

    public DocumentDto(Document document, String url, String downloadUrl) {
        this.id = document.getId();
        this.filename = document.getOriginalFilename();
        this.url = document.getFilePath();
        this.downloadUrl = document.getFilePath();
        this.fileUrl = document.getFilePath();
        this.size = document.getFileSize();
        this.uploadDate = document.getUploadDate();
        this.description = document.getDescription();
        this.contentType = document.getContentType();
        this.documentType = document.getDocumentType() != null ? document.getDocumentType().name() : null;
    }

    public static DocumentDto fromEntity(Document document) {
        if (document == null)
            return null;
        return DocumentDto.builder()
                .id(document.getId())
                .filename(document.getOriginalFilename())
                .url(document.getFilePath())
                .downloadUrl(document.getFilePath())
                .fileUrl(document.getFilePath())
                .size(document.getFileSize())
                .uploadDate(document.getUploadDate())
                .description(document.getDescription())
                .contentType(document.getContentType())
                .documentType(document.getDocumentType() != null ? document.getDocumentType().name() : null)
                .build();
    }
}
