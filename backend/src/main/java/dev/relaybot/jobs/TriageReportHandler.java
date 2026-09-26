package dev.relaybot.jobs;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.ai.GroqClient;
import dev.relaybot.ai.Triage;
import dev.relaybot.common.PermanentException;
import dev.relaybot.common.RetryableException;
import dev.relaybot.guild.Guild;
import dev.relaybot.guild.GuildRepository;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportRepository;
import dev.relaybot.report.RuleEngine;
import org.springframework.stereotype.Component;

/**
 * Runs AI triage (optional), applies the priority rule, then fans out the reply, the channel post and
 * the mirror as separate jobs. If the AI is down, we retry a couple of times and then carry on without
 * it: a report should never be stuck because an optional enrichment step failed.
 */
@Component
class TriageReportHandler implements JobHandler {

    private static final int AI_ATTEMPTS = 3;

    private final ReportRepository reports;
    private final GuildRepository guilds;
    private final GroqClient groq;
    private final ActivityService activity;

    TriageReportHandler(ReportRepository reports, GuildRepository guilds, GroqClient groq, ActivityService activity) {
        this.reports = reports;
        this.guilds = guilds;
        this.groq = groq;
        this.activity = activity;
    }

    @Override
    public String type() {
        return JobTypes.TRIAGE_REPORT;
    }

    @Override
    public void handle(Job job, JobContext ctx) {
        JobTypes.TriagePayload payload = ctx.payload(JobTypes.TriagePayload.class);
        Report report = reports.find(payload.reportId()).orElseThrow(() -> new PermanentException("Report no longer exists"));
        Guild guild = guilds.find(report.guildId()).orElseThrow(() -> new PermanentException("Server was disconnected"));

        Triage triage = null;
        String aiStatus = "SKIPPED";
        if (guild.aiEnabled() && groq.enabled()) {
            try {
                triage = groq.triage(report.text());
                aiStatus = "DONE";
            } catch (RetryableException e) {
                if (job.attempts() < AI_ATTEMPTS) {
                    throw e;
                }
                aiStatus = "FAILED";
                activity.warn(job.refs(), "ai.unavailable",
                        "AI triage unavailable for report #" + report.id() + ", continuing without it: " + e.getMessage());
            } catch (PermanentException e) {
                aiStatus = "FAILED";
                activity.warn(job.refs(), "ai.unavailable",
                        "AI triage failed for report #" + report.id() + ", continuing without it: " + e.getMessage());
            }
        }

        RuleEngine.Decision decision = RuleEngine.decide(report.text(), guilds.rules(guild.guildId()),
                triage == null ? null : triage.urgency(), guild.defaultPriority());
        reports.applyTriage(report.id(), decision, aiStatus,
                triage == null ? null : triage.summary(), triage == null ? null : triage.category());

        String why = switch (decision.source()) {
            case "RULE" -> "keyword \"" + decision.matchedKeyword() + "\"";
            case "AI" -> "AI suggestion";
            default -> "server default";
        };
        activity.info(job.refs(), "report.triaged",
                "Report #" + report.id() + " set to " + decision.priority() + " (" + why + ")"
                        + (triage != null ? ", AI category: " + triage.category() : "") + ".");

        ctx.followUp(JobTypes.REPLY_TO_USER, new JobTypes.ReplyPayload(report.id(), payload.interactionToken()), 5);
        if (guild.postChannelId() != null) {
            ctx.followUp(JobTypes.POST_TO_CHANNEL, new JobTypes.ReportPayload(report.id()), 6);
        }
        if (guild.mirrorConfigured() && decision.priority().atLeast(guild.mirrorMinPriority())) {
            ctx.followUp(JobTypes.MIRROR, new JobTypes.MirrorPayload(report.id(), "created", report.username()), 8);
        }
    }
}
