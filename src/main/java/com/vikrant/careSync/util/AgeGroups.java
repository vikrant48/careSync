package com.vikrant.careSync.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Age bands used by the analytics and reporting views. */
public final class AgeGroups {

    private AgeGroups() {
    }

    public static String of(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return "Unknown";
        }
        long age = ChronoUnit.YEARS.between(dateOfBirth, LocalDate.now());
        if (age < 18) {
            return "Under 18";
        } else if (age < 30) {
            return "18-29";
        } else if (age < 45) {
            return "30-44";
        } else if (age < 60) {
            return "45-59";
        } else if (age < 75) {
            return "60-74";
        }
        return "75+";
    }
}
