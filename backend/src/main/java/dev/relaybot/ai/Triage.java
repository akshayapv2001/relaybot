package dev.relaybot.ai;

import dev.relaybot.report.Priority;

public record Triage(String summary, String category, Priority urgency) {
}
