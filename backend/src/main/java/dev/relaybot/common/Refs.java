package dev.relaybot.common;

/** Identifiers that tie a log line, activity entry or job back to what caused it. */
public record Refs(String guildId, String interactionId, Long reportId, Long jobId) {

    public static Refs none() {
        return new Refs(null, null, null, null);
    }

    public static Refs guild(String guildId) {
        return new Refs(guildId, null, null, null);
    }

    public static Refs of(String guildId, String interactionId) {
        return new Refs(guildId, interactionId, null, null);
    }

    public Refs withReport(Long id) {
        return new Refs(guildId, interactionId, id, jobId);
    }

    public Refs withJob(Long id) {
        return new Refs(guildId, interactionId, reportId, id);
    }
}
