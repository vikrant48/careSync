package com.vikrant.careSync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreVisitIntakeRequest {

    @NotBlank(message = "Chief complaint is required")
    private String chiefComplaint;

    private String symptoms;

    private String symptomDuration;

    private String currentMedications;

    private String allergies;
}
