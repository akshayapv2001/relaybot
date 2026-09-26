package dev.relaybot.report;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The configurable rule: decides a report's priority.
 *
 * <ol>
 *   <li>Keyword rules configured per server in the dashboard. Whole-word, case-insensitive.
 *       If several match, the highest priority wins.</li>
 *   <li>Otherwise the AI's suggested urgency, if AI triage ran.</li>
 *   <li>Otherwise the server's default priority.</li>
 * </ol>
 * Admin-defined rules deliberately beat the AI: they are explicit, predictable and auditable.
 */
public final class RuleEngine {

    public record Decision(Priority priority, String source, String matchedKeyword) {
    }

    private RuleEngine() {
    }

    public static Decision decide(String text, List<KeywordRule> rules, Priority aiSuggestion, Priority defaultPriority) {
        KeywordRule best = null;
        String haystack = text == null ? "" : text;
        for (KeywordRule rule : rules) {
            if (matches(haystack, rule.keyword()) && (best == null || rule.priority().compareTo(best.priority()) > 0)) {
                best = rule;
            }
        }
        if (best != null) {
            return new Decision(best.priority(), "RULE", best.keyword());
        }
        if (aiSuggestion != null) {
            return new Decision(aiSuggestion, "AI", null);
        }
        return new Decision(defaultPriority, "DEFAULT", null);
    }

    static boolean matches(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        // Letter/digit boundaries instead of \b so keywords like "500" or "c++" behave sensibly.
        Pattern p = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword.trim().toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])");
        return p.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
