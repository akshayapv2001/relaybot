package dev.relaybot.guild;

import dev.relaybot.report.Priority;

import java.time.Instant;

public record Guild(
        String guildId,
        String name,
        String postChannelId,
        boolean reportEnabled,
        boolean statusEnabled,
        boolean aiEnabled,
        boolean ephemeralReplies,
        Priority defaultPriority,
        Priority mirrorMinPriority,
        String mirrorWebhookEnc,
        String mirrorKind,
        Instant connectedAt) {

    public boolean mirrorConfigured() {
        return mirrorWebhookEnc != null;
    }
}
