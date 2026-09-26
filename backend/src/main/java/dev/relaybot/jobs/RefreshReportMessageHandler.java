package dev.relaybot.jobs;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.PermanentException;
import dev.relaybot.discord.DiscordApi;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportMessages;
import dev.relaybot.report.ReportRepository;
import org.springframework.stereotype.Component;

/** After a button click, re-renders the report card with its new status and button states. */
@Component
class RefreshReportMessageHandler implements JobHandler {

    private final ReportRepository reports;
    private final DiscordApi discord;
    private final ActivityService activity;

    RefreshReportMessageHandler(ReportRepository reports, DiscordApi discord, ActivityService activity) {
        this.reports = reports;
        this.discord = discord;
        this.activity = activity;
    }

    @Override
    public String type() {
        return JobTypes.REFRESH_REPORT_MESSAGE;
    }

    @Override
    public void handle(Job job, JobContext ctx) {
        JobTypes.RefreshPayload payload = ctx.payload(JobTypes.RefreshPayload.class);
        Report report = reports.find(payload.reportId()).orElseThrow(() -> new PermanentException("Report no longer exists"));
        discord.editOriginalResponse(payload.interactionToken(), ReportMessages.channelCard(report));
        activity.info(job.refs(), "discord.updated", "Updated the card for report #" + report.id() + ": " + ReportMessages.statusLabel(report) + ".");
    }
}
