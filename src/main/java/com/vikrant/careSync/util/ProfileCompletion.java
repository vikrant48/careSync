package com.vikrant.careSync.util;

/**
 * Adds up weighted profile sections: a section counts when all of its fields are filled in.
 * Shared by the patient and doctor completion scores (each keeps its own sections and weights).
 */
public final class ProfileCompletion {

    private int percentage;

    public ProfileCompletion section(int weight, boolean complete) {
        if (complete) {
            percentage += weight;
        }
        return this;
    }

    public int total() {
        return percentage;
    }

    /** True when every value is present and not empty. */
    public static boolean filled(String... values) {
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
