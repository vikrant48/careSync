package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.entity.Patient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AppointmentDetailsResponse extends AppointmentResponse {

    private Long doctorId;
    private Integer doctorExperienceYears;
    private List<EducationSummary> doctorEducation;
    private List<String> doctorLanguages;
    private Double doctorAverageRating;
    private Long doctorReviewCount;

    private String patientBloodGroup;
    private String patientGender;
    private LocalDate patientDateOfBirth;
    private Integer patientAge;

    private Appointment.BookingSource bookingSource;
    private String clinicName;
    private String clinicAddress;

    public AppointmentDetailsResponse(
            Appointment appointment,
            List<Experience> experiences,
            List<Education> educations,
            List<Feedback> feedbacks) {
        super(appointment);
        this.bookingSource = appointment.getBookingSource();

        Doctor doctor = appointment.getDoctor();
        if (doctor != null) {
            this.doctorId = doctor.getId();
            this.doctorExperienceYears = experiences.stream()
                    .mapToInt(experience -> Math.max(0, experience.getYearsOfService()))
                    .sum();
            this.doctorEducation = educations.stream()
                    .sorted(Comparator.comparingInt(Education::getYearOfCompletion).reversed())
                    .map(education -> new EducationSummary(
                            education.getDegree(),
                            education.getInstitution(),
                            education.getYearOfCompletion()))
                    .toList();
            this.doctorLanguages = splitLanguages(doctor.getLanguages());
            this.doctorAverageRating = feedbacks.stream()
                    .mapToInt(Feedback::getRating)
                    .average()
                    .orElse(0.0);
            this.doctorReviewCount = (long) feedbacks.size();
            this.clinicName = experiences.stream()
                    .filter(experience -> hasText(experience.getHospitalName()))
                    .max(Comparator.comparing(
                            Experience::getId,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(Experience::getHospitalName)
                    .orElse(null);
            this.clinicAddress = doctor.getAddress();
        }

        Patient patient = appointment.getPatient();
        if (patient != null) {
            this.patientBloodGroup = patient.getBloodGroup();
            this.patientGender = patient.getGender();
            this.patientDateOfBirth = patient.getDateOfBirth();
            if (patient.getDateOfBirth() != null && !patient.getDateOfBirth().isAfter(LocalDate.now())) {
                this.patientAge = Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
            }
        }
    }

    private static List<String> splitLanguages(String languages) {
        if (!hasText(languages)) {
            return List.of();
        }
        return Arrays.stream(languages.split(","))
                .map(String::trim)
                .filter(AppointmentDetailsResponse::hasText)
                .distinct()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationSummary {
        private String degree;
        private String institution;
        private Integer yearOfCompletion;
    }
}
