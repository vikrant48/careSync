package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Education;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EducationDto {
    private Long id;
    private String degree;
    private String institution;
    private int yearOfCompletion;
    private String details;

    public EducationDto(Education education) {
        this.id = education.getId();
        this.degree = education.getDegree();
        this.institution = education.getInstitution();
        this.yearOfCompletion = education.getYearOfCompletion();
        this.details = education.getDetails();
    }
}
