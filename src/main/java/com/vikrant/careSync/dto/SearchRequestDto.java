package com.vikrant.careSync.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequestDto {
    private String query;
    private String specialization;
    private String location;
    private String gender;
    /** Doctor search: true = verified only, false = not verified only, null = all */
    private Boolean verified;
    private String date;
    private String status;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
