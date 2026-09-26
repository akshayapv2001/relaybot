package dev.relaybot.jobs;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.PermanentException;
import dev.relaybot.discord.DiscordApi;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportMessages;
import dev.relaybot.report.ReportRepository;
import org.springframework.stereotype.Component;

/** Replaces the deferred "thinking…" response. Editing @original is idempotent, so re-runs are safe. */
@Component
class ReplyToUserHandler implements JobHandler {

    private final ReportRepository reports;
    private final DiscordApi discord;
    private final ActivityService activity;

    ReplyToUserHandler(ReportRepository reports, DiscordApi discord, ActivityService activity) {
        this.reports = reports;
        this.discord = discord;
        this.activity = activity;
    }

    @Override
    public String type() {
        return JobTypes.REPLY_TO_USER;
    }

    @Override
    public void handle(Job job, JobContext ctx) {
        JobTypes.ReplyPayload payload = ctx.payload(JobTypes.ReplyPayload.class);
        Report report = reports.find(payload.reportId()).orElseThrow(() -> new PermanentException("Report no longer exists"));
        discord.editOriginalResponse(payload.interactionToken(), ReportMessages.reply(report));
        activity.info(job.refs(), "discord.replied", "Replied in Discord to report #" + report.id() + ".");
    }
}
