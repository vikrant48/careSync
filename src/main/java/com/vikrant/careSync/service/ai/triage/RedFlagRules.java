package com.vikrant.careSync.service.ai.triage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic emergency red flags, shared by the chat, streaming chat and the pre-visit intake. Works without
 * any AI (instant, no quota) and is the first of two triage layers; the AI's own urgency check is the second.
 *
 * Matching is by whole words, so "heatstroke" is not "stroke". A medical phrase is ignored when a negation comes
 * just before it in the same clause ("no chest pain", "I don't have trouble breathing"). Mental-health crisis
 * phrases are never dropped silently: a negated one is left to the AI check.
 */
public final class RedFlagRules {

    /** A red flag found in the text: its category and the phrase that matched (for audit and display). */
    public record Match(TriageCategory category, String phrase) {
    }

    private static final List<Pattern> MEDICAL = compile(
            // Heart
            "chest pain", "pain in (my |the )?chest", "chest (tightness|pressure)", "tight(ness)? in (my |the )?chest",
            "heart attack",
            // Breathing
            "can'?t breathe", "cannot breathe", "unable to breathe", "not able to breathe", "not breathing",
            "difficulty breathing", "trouble breathing", "struggling to breathe", "short(ness)? of breath", "choking",
            "turning blue", "blue lips",
            // Bleeding
            "(severe|heavy|uncontrolled|massive) bleeding", "bleeding (heavily|a lot|non-?stop)",
            "(won'?t|will not|doesn'?t|does not) stop bleeding", "bleeding (won'?t|will not|doesn'?t|does not) stop",
            "(coughing|vomiting|throwing) up blood",
            "(coughing|vomiting) blood", "blood in (my )?vomit",
            // Consciousness, seizures
            "unconscious", "unresponsive", "passed out", "fainted", "not waking up", "seizures?", "convulsions?",
            "having a fit",
            // Stroke signs ("heat stroke" / "sun stroke" are not stroke)
            "(?<!heat )(?<!sun )stroke", "face (is )?drooping", "slurred speech",
            "(sudden )?(weakness|numbness) (in|on|of) one side",
            // Allergy, poisoning, burns
            "anaphyla(xis|ctic)", "throat (is )?(closing|swelling)", "swollen throat", "severe allergic reaction",
            "overdosed?", "swallowed (poison|bleach|pesticide|acid|kerosene)", "poisoning", "severe burns?",
            // Common Hindi (Roman script)
            "seene (mein|me|mai) (dard|dukh)", "saa?ns (nahi|nahin) (aa|le|ho)", "saa?ns lene (mein|me|mai) (taklif|takleef|dikkat)",
            "behosh");

    private static final List<Pattern> MENTAL_HEALTH = compile(
            "suicid(e|al)", "kill (my ?self|myself)", "end (my life|it all)", "want to die", "wanna die",
            "take my (own )?life", "self[- ]?harm", "hurt(ing)? myself", "cut(ting)? myself", "no reason to live",
            "better off dead",
            // Common Hindi (Roman script)
            "khudkushi", "aatmahatya", "atmahatya", "marna chaha?t(a|i)", "jeena nahi(n)? chaha?t(a|i)");

    private static final Pattern CLAUSE_BREAK = Pattern.compile("[.;!?\\n]|,|\\bbut\\b|\\bhowever\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(no|not|never|without|none|denies|denied|deny|don'?t|doesn'?t|didn'?t|isn'?t|wasn'?t|haven'?t|"
                    + "hasn'?t|free of)\\b");
    private static final int NEGATION_WINDOW_WORDS = 4;

    private RedFlagRules() {
    }

    /** First red flag in the text: mental-health crisis first (it needs a different response), then medical. */
    public static Optional<Match> find(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('’', '\'');
        for (Pattern pattern : MENTAL_HEALTH) {
            Matcher m = pattern.matcher(lower);
            while (m.find()) {
                if (!negated(lower, m.start())) {
                    return Optional.of(new Match(TriageCategory.MENTAL_HEALTH, m.group()));
                }
            }
        }
        for (Pattern pattern : MEDICAL) {
            Matcher m = pattern.matcher(lower);
            while (m.find()) {
                if (!negated(lower, m.start())) {
                    return Optional.of(new Match(TriageCategory.MEDICAL, m.group()));
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isEmergency(String text) {
        return find(text).isPresent();
    }

    /** A negation word within the few words before the phrase, in the same clause */
    static boolean negated(String lower, int phraseStart) {
        String before = lower.substring(0, phraseStart);
        Matcher breaks = CLAUSE_BREAK.matcher(before);
        int clauseStart = 0;
        while (breaks.find()) {
            clauseStart = breaks.end();
        }
        String[] words = before.substring(clauseStart).trim().split("\\s+");
        int from = Math.max(0, words.length - NEGATION_WINDOW_WORDS);
        String window = String.join(" ", java.util.Arrays.copyOfRange(words, from, words.length));
        return NEGATION.matcher(window).find();
    }

    private static List<Pattern> compile(String... phrases) {
        return java.util.Arrays.stream(phrases)
                .map(p -> Pattern.compile("\\b" + p + "\\b"))
                .toList();
    }
}
