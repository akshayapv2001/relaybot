package dev.relaybot.report;

import java.util.Locale;

public enum Priority {
    LOW, MEDIUM, HIGH;

    public boolean atLeast(Priority other) {
        return compareTo(other) >= 0;
    }

    /** Lenient parse for values coming from the database or an LLM; returns null if unknown. */
    public static Priority parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> LOW;
            case "MEDIUM", "MED", "NORMAL" -> MEDIUM;
            case "HIGH", "URGENT", "CRITICAL" -> HIGH;
            default -> null;
        };
    }
}
