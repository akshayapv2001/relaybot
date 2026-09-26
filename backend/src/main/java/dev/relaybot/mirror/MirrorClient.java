package dev.relaybot.mirror;

import dev.relaybot.common.HttpCalls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Sends a plain-text notification to the second channel (Slack or Discord webhook). */
@Component
public class MirrorClient {

    private final RestClient http;

    public MirrorClient(@Qualifier("external") RestClient http) {
        this.http = http;
    }

    public void send(MirrorTarget target, String text) {
        Map<String, Object> body = switch (target.kind()) {
            case SLACK -> Map.of("text", escapeSlack(text));
            // allowed_mentions: user text can never trigger @everyone / role pings.
            case DISCORD -> Map.of(
                    "content", text.length() > 2000 ? text.substring(0, 1999) + "…" : text,
                    "username", "RelayBot",
                    "allowed_mentions", Map.of("parse", List.of()));
        };
        HttpCalls.execute(target.kind() == MirrorTarget.Kind.SLACK ? "Slack mirror" : "Discord mirror",
                // A URI (not a template string) so nothing in the URL is treated as {placeholders}.
                http.post().uri(URI.create(target.url())).contentType(MediaType.APPLICATION_JSON).body(body),
                Void.class);
    }

    /** Slack treats <, > and & as control characters (e.g. <!channel>); escape them in user text. */
    static String escapeSlack(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
