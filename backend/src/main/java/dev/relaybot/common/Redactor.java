package dev.relaybot.common;

import java.util.regex.Pattern;

/**
 * Last line of defence against secrets in logs and error messages. HTTP client exceptions
 * often embed the full request URL, and webhook/interaction URLs contain credentials.
 */
public final class Redactor {

    private static final Pattern SECRET_URL = Pattern.compile(
            "https?://\\S*(?:/webhooks/|hooks\\.slack\\.com|/interactions/)\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOT_TOKEN = Pattern.compile("(?i)(Bot|Bearer)\\s+[A-Za-z0-9._\\-]{20,}");

    private Redactor() {
    }

    public static String redact(String s) {
        if (s == null) {
            return null;
        }
        String out = SECRET_URL.matcher(s).replaceAll("[redacted-url]");
        out = BOT_TOKEN.matcher(out).replaceAll("$1 [redacted]");
        return out.length() > 500 ? out.substring(0, 500) + "…" : out;
    }
}
