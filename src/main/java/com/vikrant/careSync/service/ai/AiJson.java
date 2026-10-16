package com.vikrant.careSync.service.ai;

/** Helpers for model answers that should be JSON but may be wrapped in prose or code fences. */
public final class AiJson {

    private AiJson() {
    }

    /** The outermost {...} in the text, the trimmed text when there is none, or "{}" for null. */
    public static String extractObject(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}
