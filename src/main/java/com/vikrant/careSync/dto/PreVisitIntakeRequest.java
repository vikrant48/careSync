package com.vikrant.careSync.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreVisitIntakeRequest {

    @NotBlank(message = "Chief complaint is required")
    private String chiefComplaint;

    private String symptoms;

    @JsonAlias({ "symptomDuration", "duration" })
    private String symptomDuration;

    private String severity;

    private String currentMedications;

    private String allergies;

    private Boolean consentGiven;

    @JsonSetter("symptoms")
    public void setSymptomsFromJson(Object symptomsObj) {
        if (symptomsObj == null) {
            this.symptoms = "";
        } else if (symptomsObj instanceof Collection) {
            this.symptoms = String.join(", ", ((Collection<?>) symptomsObj).stream().map(Object::toString).toList());
        } else {
            this.symptoms = symptomsObj.toString();
        }
    }
}
