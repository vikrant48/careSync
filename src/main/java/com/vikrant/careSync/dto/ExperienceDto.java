package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Experience;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperienceDto {
    private Long id;
    private String hospitalName;
    private String position;
    private int yearsOfService;
    private String details;

    public ExperienceDto(Experience experience) {
        this.id = experience.getId();
        this.hospitalName = experience.getHospitalName();
        this.position = experience.getPosition();
        this.yearsOfService = experience.getYearsOfService();
        this.details = experience.getDetails();
    }
}
