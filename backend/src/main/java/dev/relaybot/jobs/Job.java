package dev.relaybot.jobs;

import dev.relaybot.common.Refs;

public record Job(long id, String type, String guildId, String interactionId, Long reportId,
                  String payload, int attempts, int maxAttempts) {

    public Refs refs() {
        return new Refs(guildId, interactionId, reportId, id);
    }

    public boolean isLastAttempt() {
        return attempts >= maxAttempts;
    }
}
