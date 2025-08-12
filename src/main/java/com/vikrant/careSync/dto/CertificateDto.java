package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Certificate;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateDto {
    private Long id;
    private String name;
    private String url;
    private String details;

    public CertificateDto(Certificate certificate) {
        this.id = certificate.getId();
        this.name = certificate.getName();
        this.url = certificate.getUrl();
        this.details = certificate.getDetails();
    }
}
