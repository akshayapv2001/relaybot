package dev.relaybot.report;

import java.time.Instant;

public record Report(
        long id,
        String guildId,
        String interactionId,
        String channelId,
        String userId,
        String username,
        String text,
        Priority priority,
        String prioritySource,
        String matchedKeyword,
        String aiStatus,
        String aiSummary,
        String aiCategory,
        String status,
        String actedBy,
        String postChannelId,
        String channelMessageId,
        Instant createdAt) {

    public static final String OPEN = "OPEN";
    public static final String ESCALATED = "ESCALATED";
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
}
