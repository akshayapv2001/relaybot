package dev.relaybot.discord;

import java.util.List;
import java.util.Map;

/** Builders for the synchronous HTTP response Discord expects within ~3 seconds. */
public final class InteractionResponses {

    // Interaction types (request)
    public static final int PING = 1;
    public static final int APPLICATION_COMMAND = 2;
    public static final int MESSAGE_COMPONENT = 3;
    public static final int MODAL_SUBMIT = 5;

    // Custom ids we own. Prefixed so we can ignore anything that isn't ours.
    public static final String REPORT_MODAL_ID = "relaybot:report_modal";
    public static final String REPORT_TEXT_INPUT_ID = "report_text";
    public static final String ACK_PREFIX = "relaybot:ack:";
    public static final String ESCALATE_PREFIX = "relaybot:esc:";

    private static final int EPHEMERAL = 1 << 6;
    private static final Map<String, Object> NO_MENTIONS = Map.of("parse", List.of());

    private InteractionResponses() {
    }

    public static Map<String, Object> pong() {
        return Map.of("type", 1);
    }

    /** Type 4: reply immediately. */
    public static Map<String, Object> message(String content, boolean ephemeral) {
        return Map.of("type", 4, "data", Map.of(
                "content", content,
                "flags", ephemeral ? EPHEMERAL : 0,
                "allowed_mentions", NO_MENTIONS));
    }

    public static Map<String, Object> ephemeral(String content) {
        return message(content, true);
    }

    /** Type 5: "RelayBot is thinking…". The real answer is sent later by a job (token valid 15 min). */
    public static Map<String, Object> deferredMessage(boolean ephemeral) {
        return Map.of("type", 5, "data", Map.of("flags", ephemeral ? EPHEMERAL : 0));
    }

    /** Type 6: acknowledge a button click; the message is edited later by a job. */
    public static Map<String, Object> deferredUpdate() {
        return Map.of("type", 6);
    }

    /** Type 9: open the /report form. */
    public static Map<String, Object> reportModal() {
        Map<String, Object> input = Map.of(
                "type", 4,               // text input
                "custom_id", REPORT_TEXT_INPUT_ID,
                "style", 2,              // paragraph
                "label", "What happened?",
                "placeholder", "Describe the problem, what you expected, and who is affected.",
                "min_length", 5,
                "max_length", 1000,
                "required", true);
        return Map.of("type", 9, "data", Map.of(
                "custom_id", REPORT_MODAL_ID,
                "title", "Report a problem",
                "components", List.of(Map.of("type", 1, "components", List.of(input)))));
    }
}
