package dev.relaybot.activity;

import java.time.Instant;

public record Activity(long id, String guildId, String interactionId, Long reportId, Long jobId,
                       String level, String event, String message, Instant createdAt) {
}
