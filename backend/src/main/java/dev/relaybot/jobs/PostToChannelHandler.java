package dev.relaybot.jobs;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.PermanentException;
import dev.relaybot.discord.DiscordApi;
import dev.relaybot.guild.Guild;
import dev.relaybot.guild.GuildRepository;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportMessages;
import dev.relaybot.report.ReportRepository;
import org.springframework.stereotype.Component;

/**
 * Posts the report card (with buttons) to the server's configured channel. Skips if a message id is
 * already stored, so a retry after success does not post twice.
 */
@Component
class PostToChannelHandler implements JobHandler {

    private final ReportRepository reports;
    private final GuildRepository guilds;
    private final DiscordApi discord;
    private final ActivityService activity;

    PostToChannelHandler(ReportRepository reports, GuildRepository guilds, DiscordApi discord, ActivityService activity) {
        this.reports = reports;
        this.guilds = guilds;
        this.discord = discord;
        this.activity = activity;
    }

    @Override
    public String type() {
        return JobTypes.POST_TO_CHANNEL;
    }

    @Override
    public void handle(Job job, JobContext ctx) {
        JobTypes.ReportPayload payload = ctx.payload(JobTypes.ReportPayload.class);
        Report report = reports.find(payload.reportId()).orElseThrow(() -> new PermanentException("Report no longer exists"));
        if (report.channelMessageId() != null) {
            return;
        }
        Guild guild = guilds.find(report.guildId()).orElseThrow(() -> new PermanentException("Server was disconnected"));
        String channelId = guild.postChannelId();
        if (channelId == null) {
            return;
        }
        String messageId = discord.createChannelMessage(channelId, ReportMessages.channelCard(report));
        reports.setChannelMessage(report.id(), channelId, messageId);
        activity.info(job.refs(), "discord.posted", "Posted report #" + report.id() + " to the report channel.");
    }
}
