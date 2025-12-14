package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private Long id;
    private String originalFilename;
    private String fileUrl;
    private String documentType;
    private String description;
    private LocalDateTime uploadDate;
    private String uploadedByUsername;

    public static DocumentDto fromEntity(Document document) {
        return DocumentDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .fileUrl(document.getFileUrl())
                .documentType(document.getDocumentType().name())
                .description(document.getDescription())
                .uploadDate(document.getUploadDate())
                .uploadedByUsername(document.getUploadedByUsername())
                .build();
    }
}
