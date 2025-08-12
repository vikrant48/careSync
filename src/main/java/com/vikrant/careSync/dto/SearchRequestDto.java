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
    private String date;
    private String status;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
