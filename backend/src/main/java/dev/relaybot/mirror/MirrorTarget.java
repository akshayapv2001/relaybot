package dev.relaybot.mirror;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a mirror webhook URL and works out whether it is Slack or Discord.
 * Only these two hosts are allowed, which also stops the server being used to
 * call arbitrary internal URLs (SSRF).
 */
public record MirrorTarget(Kind kind, String url) {

    public enum Kind { SLACK, DISCORD }

    private static final Set<String> DISCORD_HOSTS = Set.of(
            "discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");

    public static MirrorTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("That doesn't look like a URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Webhook URL must be a plain https:// URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (host.equals("hooks.slack.com") && path.startsWith("/services/")) {
            return new MirrorTarget(Kind.SLACK, uri.toString());
        }
        if (DISCORD_HOSTS.contains(host) && path.startsWith("/api/webhooks/")) {
            return new MirrorTarget(Kind.DISCORD, uri.toString());
        }
        throw new IllegalArgumentException(
                "Use a Slack Incoming Webhook (hooks.slack.com/services/...) or a Discord channel webhook (discord.com/api/webhooks/...)");
    }

    @Override
    public String toString() {
        return "MirrorTarget[" + kind + ", url=<redacted>]";   // never print the URL
    }
}
