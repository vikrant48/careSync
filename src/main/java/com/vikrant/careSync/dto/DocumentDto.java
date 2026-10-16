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
        this.url = url;
        this.downloadUrl = downloadUrl;
        this.fileUrl = url;
        this.size = document.getFileSize();
        this.uploadDate = document.getUploadDate();
        this.description = document.getDescription();
        this.contentType = document.getContentType();
        this.documentType = document.getDocumentType() != null ? document.getDocumentType().name() : null;
    }

}
