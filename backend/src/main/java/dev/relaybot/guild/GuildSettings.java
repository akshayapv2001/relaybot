package dev.relaybot.guild;

import dev.relaybot.report.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** The command behaviour an admin can change from the dashboard. */
public record GuildSettings(
        @Pattern(regexp = "\\d{17,20}", message = "must be a Discord channel id") String postChannelId,
        boolean reportEnabled,
        boolean statusEnabled,
        boolean aiEnabled,
        boolean ephemeralReplies,
        @NotNull Priority defaultPriority,
        @NotNull Priority mirrorMinPriority) {
}
