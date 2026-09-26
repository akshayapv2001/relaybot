package dev.relaybot.report;

import dev.relaybot.discord.InteractionResponses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything RelayBot writes about a report, in one place so wording stays consistent. */
public final class ReportMessages {

    private static final Map<String, Object> NO_MENTIONS = Map.of("parse", List.of());

    private ReportMessages() {
    }

    /** The answer to the person who ran /report (replaces the "thinking…" placeholder). */
    public static Map<String, Object> reply(Report r) {
        StringBuilder text = new StringBuilder()
                .append("Report #").append(r.id()).append(" logged as **").append(label(r.priority())).append("** priority");
        if ("RULE".equals(r.prioritySource())) {
            text.append(" (matched the keyword \"").append(r.matchedKeyword()).append("\")");
        } else if ("AI".equals(r.prioritySource())) {
            text.append(" (suggested by AI triage)");
        }
        text.append('.');
        if (r.aiSummary() != null) {
            text.append("\n> ").append(r.aiSummary());
        }
        return Map.of("content", text.toString(), "allowed_mentions", NO_MENTIONS);
    }

    /** The card posted to the team channel, with Acknowledge / Escalate buttons. */
    public static Map<String, Object> channelCard(Report r) {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("Reported by", r.username(), true));
        fields.add(field("Status", statusLabel(r), true));
        if (r.aiCategory() != null) {
            fields.add(field("Category", r.aiCategory(), true));
        }
        if (r.aiSummary() != null) {
            fields.add(field("AI summary", r.aiSummary(), false));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Report #" + r.id() + ": " + label(r.priority()) + " priority");
        embed.put("description", truncate(r.text(), 3500));
        embed.put("color", color(r.priority()));
        embed.put("fields", fields);
        embed.put("footer", Map.of("text", "RelayBot"));
        if (r.createdAt() != null) {
            embed.put("timestamp", r.createdAt().toString());
        }

        boolean acknowledged = Report.ACKNOWLEDGED.equals(r.status());
        List<Map<String, Object>> buttons = List.of(
                button(3, acknowledged ? "Acknowledged" : "Acknowledge", InteractionResponses.ACK_PREFIX + r.id(), acknowledged),
                button(4, "Escalate", InteractionResponses.ESCALATE_PREFIX + r.id(), !Report.OPEN.equals(r.status())));

        return Map.of(
                "embeds", List.of(embed),
                "components", List.of(Map.of("type", 1, "components", buttons)),
                "allowed_mentions", NO_MENTIONS);
    }

    /** Plain text for the second channel (Slack / Discord webhook). */
    public static String mirrorText(Report r, String guildName, String event, String actor) {
        String prefix = "[" + guildName + "] ";
        return switch (event) {
            case "acknowledged" -> prefix + "Report #" + r.id() + " was acknowledged by " + actor + ".";
            case "escalated" -> prefix + "Report #" + r.id() + " was escalated to HIGH by " + actor + ": " + truncate(r.text(), 300);
            default -> prefix + "New " + label(r.priority()) + " report #" + r.id() + " from " + r.username() + ": "
                    + truncate(r.text(), 500) + (r.aiSummary() != null ? "\nAI summary: " + r.aiSummary() : "");
        };
    }

    public static String statusLabel(Report r) {
        return switch (r.status()) {
            case Report.ACKNOWLEDGED -> "Acknowledged by " + r.actedBy();
            case Report.ESCALATED -> "Escalated by " + r.actedBy();
            default -> "Open";
        };
    }

    private static String label(Priority p) {
        return p == null ? "Unrated" : switch (p) {
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
        };
    }

    private static int color(Priority p) {
        if (p == null) return 0x8A94A6;
        return switch (p) {
            case HIGH -> 0xD92D20;
            case MEDIUM -> 0xDC6803;
            case LOW -> 0x3E7C59;
        };
    }

    private static Map<String, Object> field(String name, String value, boolean inline) {
        return Map.of("name", name, "value", truncate(value == null || value.isBlank() ? "-" : value, 1000), "inline", inline);
    }

    private static Map<String, Object> button(int style, String label, String customId, boolean disabled) {
        return Map.of("type", 2, "style", style, "label", label, "custom_id", customId, "disabled", disabled);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
