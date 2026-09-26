package dev.relaybot.jobs;

/**
 * A /report fans out into small independent steps. Each step retries on its own, so a Slack outage
 * delays only the mirror step, never the reply in Discord.
 *
 * <pre>
 * TRIAGE_REPORT ─┬─> REPLY_TO_USER      (edit the deferred "thinking…" response)
 *                ├─> POST_TO_CHANNEL    (card with buttons in the configured channel)
 *                └─> MIRROR             (Slack / Discord webhook)
 * button click  ──> REFRESH_REPORT_MESSAGE (+ MIRROR)
 * </pre>
 */
public final class JobTypes {

    public static final String TRIAGE_REPORT = "TRIAGE_REPORT";
    public static final String REPLY_TO_USER = "REPLY_TO_USER";
    public static final String POST_TO_CHANNEL = "POST_TO_CHANNEL";
    public static final String MIRROR = "MIRROR";
    public static final String REFRESH_REPORT_MESSAGE = "REFRESH_REPORT_MESSAGE";

    public record TriagePayload(long reportId, String interactionToken) {
    }

    public record ReplyPayload(long reportId, String interactionToken) {
    }

    public record ReportPayload(long reportId) {
    }

    public record MirrorPayload(long reportId, String event, String actor) {
    }

    public record RefreshPayload(long reportId, String interactionToken) {
    }

    private JobTypes() {
    }

    public static String label(String type) {
        return switch (type) {
            case TRIAGE_REPORT -> "Triage";
            case REPLY_TO_USER -> "Reply in Discord";
            case POST_TO_CHANNEL -> "Post to channel";
            case MIRROR -> "Mirror notification";
            case REFRESH_REPORT_MESSAGE -> "Update report card";
            default -> type;
        };
    }
}
