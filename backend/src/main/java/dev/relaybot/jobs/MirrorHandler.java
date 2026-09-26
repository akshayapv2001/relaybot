package dev.relaybot.jobs;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.PermanentException;
import dev.relaybot.common.SecretBox;
import dev.relaybot.guild.Guild;
import dev.relaybot.guild.GuildRepository;
import dev.relaybot.mirror.MirrorClient;
import dev.relaybot.mirror.MirrorTarget;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportMessages;
import dev.relaybot.report.ReportRepository;
import org.springframework.stereotype.Component;

/**
 * Sends the notification to the second channel. Webhooks have no idempotency key, so this is
 * at-least-once: a crash exactly between "Slack accepted it" and "job marked done" can send twice.
 */
@Component
class MirrorHandler implements JobHandler {

    private final ReportRepository reports;
    private final GuildRepository guilds;
    private final SecretBox secrets;
    private final MirrorClient mirror;
    private final ActivityService activity;

    MirrorHandler(ReportRepository reports, GuildRepository guilds, SecretBox secrets, MirrorClient mirror,
                  ActivityService activity) {
        this.reports = reports;
        this.guilds = guilds;
        this.secrets = secrets;
        this.mirror = mirror;
        this.activity = activity;
    }

    @Override
    public String type() {
        return JobTypes.MIRROR;
    }

    @Override
    public void handle(Job job, JobContext ctx) {
        JobTypes.MirrorPayload payload = ctx.payload(JobTypes.MirrorPayload.class);
        Report report = reports.find(payload.reportId()).orElseThrow(() -> new PermanentException("Report no longer exists"));
        Guild guild = guilds.find(report.guildId()).orElseThrow(() -> new PermanentException("Server was disconnected"));
        if (!guild.mirrorConfigured()) {
            activity.info(job.refs(), "mirror.skipped", "Mirror was removed before report #" + report.id() + " could be sent.");
            return;
        }
        MirrorTarget target = MirrorTarget.parse(secrets.decrypt(guild.mirrorWebhookEnc()));
        mirror.send(target, ReportMessages.mirrorText(report, guild.name(), payload.event(), payload.actor()));
        activity.info(job.refs(), "mirror.sent",
                "Mirrored report #" + report.id() + " (" + payload.event() + ") to " + (target.kind() == MirrorTarget.Kind.SLACK ? "Slack" : "Discord webhook") + ".");
    }
}
