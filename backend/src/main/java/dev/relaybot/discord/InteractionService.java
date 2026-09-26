package dev.relaybot.discord;

import com.fasterxml.jackson.databind.JsonNode;
import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.Refs;
import dev.relaybot.guild.Guild;
import dev.relaybot.guild.GuildRepository;
import dev.relaybot.jobs.JobQueue;
import dev.relaybot.jobs.JobTypes;
import dev.relaybot.report.Priority;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Handles a verified interaction inside Discord's 3-second window. Only fast database work happens
 * here: record the interaction (dedup), create the report, enqueue jobs, and answer. Anything slow
 * or failure-prone (AI, follow-ups, webhooks) runs from the job queue.
 *
 * <p>Everything happens in one transaction: the interaction row, the report and its first job are
 * committed together or not at all.
 */
@Service
public class InteractionService {

    private static final int MAX_REPORT_CHARS = 1000;

    /** Everything we need about the caller, pulled out of the raw payload once. */
    record Intake(String interactionId, String token, int type, Guild guild, String channelId,
                  String userId, String username, JsonNode data, Refs refs) {
    }

    private final InteractionRepository interactions;
    private final GuildRepository guilds;
    private final ReportRepository reports;
    private final JobQueue jobs;
    private final ActivityService activity;

    public InteractionService(InteractionRepository interactions, GuildRepository guilds, ReportRepository reports,
                              JobQueue jobs, ActivityService activity) {
        this.interactions = interactions;
        this.guilds = guilds;
        this.reports = reports;
        this.jobs = jobs;
        this.activity = activity;
    }

    @Transactional
    public Map<String, Object> handle(JsonNode payload) {
        String id = payload.path("id").asText();
        int type = payload.path("type").asInt();
        String guildId = textOrNull(payload, "guild_id");
        JsonNode user = payload.has("member") ? payload.path("member").path("user") : payload.path("user");
        String userId = user.path("id").asText(null);
        String username = displayName(payload.path("member"), user);
        String command = describe(type, payload.path("data"));
        Refs refs = Refs.of(guildId, id);

        boolean firstDelivery = interactions.tryInsert(
                new InteractionRepository.NewInteraction(id, guildId, type, command, userId, username));
        if (!firstDelivery) {
            activity.warn(refs, "interaction.duplicate", "Ignored a repeat delivery of " + command + " (already processed).");
            return InteractionResponses.ephemeral("This request was already processed.");
        }
        activity.info(refs, "interaction.received", username + " used " + command);

        if (guildId == null) {
            interactions.setOutcome(id, "REJECTED");
            return InteractionResponses.ephemeral("RelayBot only works inside a server.");
        }
        Optional<Guild> guild = guilds.find(guildId);
        if (guild.isEmpty()) {
            interactions.setOutcome(id, "REJECTED");
            activity.warn(refs, "interaction.unknown_server", "Command came from a server that isn't connected.");
            return InteractionResponses.ephemeral("This server isn't connected to RelayBot yet. Ask an admin to connect it from the dashboard.");
        }

        Intake in = new Intake(id, payload.path("token").asText(), type, guild.get(),
                textOrNull(payload, "channel_id"), userId, username, payload.path("data"), refs);

        return switch (type) {
            case InteractionResponses.APPLICATION_COMMAND -> onCommand(in);
            case InteractionResponses.MODAL_SUBMIT -> onModalSubmit(in);
            case InteractionResponses.MESSAGE_COMPONENT -> onButton(in);
            default -> reject(in, "Unsupported interaction.");
        };
    }

    private Map<String, Object> onCommand(Intake in) {
        String name = in.data().path("name").asText();
        return switch (name) {
            case "report" -> {
                if (!in.guild().reportEnabled()) {
                    yield disabled(in, "/report");
                }
                String text = optionValue(in.data(), "text");
                if (text == null || text.isBlank()) {
                    interactions.setOutcome(in.interactionId(), "MODAL_OPENED");
                    yield InteractionResponses.reportModal();
                }
                yield createReport(in, text);
            }
            case "status" -> {
                if (!in.guild().statusEnabled()) {
                    yield disabled(in, "/status");
                }
                interactions.setOutcome(in.interactionId(), "REPLIED");
                activity.info(in.refs(), "status.replied", "Replied to /status.");
                yield InteractionResponses.message(statusText(in.guild().guildId()), in.guild().ephemeralReplies());
            }
            default -> reject(in, "Unknown command.");
        };
    }

    private Map<String, Object> onModalSubmit(Intake in) {
        if (!InteractionResponses.REPORT_MODAL_ID.equals(in.data().path("custom_id").asText())) {
            return reject(in, "Unknown form.");
        }
        if (!in.guild().reportEnabled()) {
            return disabled(in, "/report");
        }
        String text = null;
        for (JsonNode row : in.data().path("components")) {
            for (JsonNode component : row.path("components")) {
                if (InteractionResponses.REPORT_TEXT_INPUT_ID.equals(component.path("custom_id").asText())) {
                    text = component.path("value").asText();
                }
            }
        }
        if (text == null || text.isBlank()) {
            return reject(in, "The report was empty.");
        }
        return createReport(in, text);
    }

    private Map<String, Object> createReport(Intake in, String rawText) {
        String text = rawText.strip();
        if (text.length() > MAX_REPORT_CHARS) {
            text = text.substring(0, MAX_REPORT_CHARS);
        }
        long reportId = reports.create(new ReportRepository.NewReport(
                in.guild().guildId(), in.interactionId(), in.channelId(), in.userId(), in.username(), text));
        Refs refs = in.refs().withReport(reportId);
        jobs.enqueue(JobTypes.TRIAGE_REPORT, refs, new JobTypes.TriagePayload(reportId, in.token()), 6);
        interactions.setOutcome(in.interactionId(), "DEFERRED");
        activity.info(refs, "report.created", "Report #" + reportId + " created by " + in.username() + ".");
        return InteractionResponses.deferredMessage(in.guild().ephemeralReplies());
    }

    private Map<String, Object> onButton(Intake in) {
        String customId = in.data().path("custom_id").asText();
        boolean ack = customId.startsWith(InteractionResponses.ACK_PREFIX);
        boolean escalate = customId.startsWith(InteractionResponses.ESCALATE_PREFIX);
        if (!ack && !escalate) {
            return reject(in, "Unknown button.");
        }
        long reportId;
        try {
            reportId = Long.parseLong(customId.substring(customId.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return reject(in, "Unknown button.");
        }
        Optional<Report> report = reports.find(reportId);
        // Multi-server isolation: a button can only act on a report from its own server.
        if (report.isEmpty() || !report.get().guildId().equals(in.guild().guildId())) {
            return reject(in, "That report doesn't exist in this server.");
        }
        Refs refs = in.refs().withReport(reportId);
        boolean changed = ack ? reports.acknowledge(reportId, in.username()) : reports.escalate(reportId, in.username());
        if (!changed) {
            interactions.setOutcome(in.interactionId(), "NO_CHANGE");
            return InteractionResponses.ephemeral("Report #" + reportId + " is already "
                    + report.get().status().toLowerCase() + ".");
        }

        String event = ack ? "acknowledged" : "escalated";
        activity.info(refs, "report." + event, "Report #" + reportId + " " + event + " by " + in.username() + ".");
        jobs.enqueue(JobTypes.REFRESH_REPORT_MESSAGE, refs, new JobTypes.RefreshPayload(reportId, in.token()), 5);
        // Escalation always mirrors (it makes the report HIGH); acknowledgements follow the priority threshold.
        if (in.guild().mirrorConfigured() && (escalate || isAtLeastMirrorPriority(report.get(), in.guild()))) {
            jobs.enqueue(JobTypes.MIRROR, refs, new JobTypes.MirrorPayload(reportId, event, in.username()), 8);
        }
        interactions.setOutcome(in.interactionId(), "DEFERRED");
        return InteractionResponses.deferredUpdate();
    }

    private static boolean isAtLeastMirrorPriority(Report r, Guild g) {
        return r.priority() != null && r.priority().atLeast(g.mirrorMinPriority());
    }

    private String statusText(String guildId) {
        ReportRepository.OpenSummary s = reports.openSummary(guildId);
        StringBuilder out = new StringBuilder("**Open reports in this server**\n")
                .append("High: ").append(s.openByPriority().get(Priority.HIGH))
                .append("   Medium: ").append(s.openByPriority().get(Priority.MEDIUM))
                .append("   Low: ").append(s.openByPriority().get(Priority.LOW));
        Instant last = s.lastReportAt();
        out.append('\n').append(last == null ? "No reports yet." : "Last report <t:" + last.getEpochSecond() + ":R>");
        return out.toString();
    }

    private Map<String, Object> disabled(Intake in, String command) {
        interactions.setOutcome(in.interactionId(), "DISABLED");
        activity.info(in.refs(), "command.disabled", command + " is turned off for this server.");
        return InteractionResponses.ephemeral(command + " is turned off in this server.");
    }

    private Map<String, Object> reject(Intake in, String message) {
        interactions.setOutcome(in.interactionId(), "IGNORED");
        return InteractionResponses.ephemeral(message);
    }

    private static String describe(int type, JsonNode data) {
        return switch (type) {
            case InteractionResponses.APPLICATION_COMMAND -> "/" + data.path("name").asText("?");
            case InteractionResponses.MODAL_SUBMIT -> "the /report form";
            case InteractionResponses.MESSAGE_COMPONENT -> {
                String id = data.path("custom_id").asText("");
                yield id.startsWith(InteractionResponses.ACK_PREFIX) ? "the Acknowledge button"
                        : id.startsWith(InteractionResponses.ESCALATE_PREFIX) ? "the Escalate button" : "a button";
            }
            default -> "interaction type " + type;
        };
    }

    private static String optionValue(JsonNode data, String name) {
        for (JsonNode option : data.path("options")) {
            if (name.equals(option.path("name").asText())) {
                return option.path("value").asText();
            }
        }
        return null;
    }

    private static String displayName(JsonNode member, JsonNode user) {
        for (String candidate : new String[]{member.path("nick").asText(null), user.path("global_name").asText(null),
                user.path("username").asText(null)}) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "unknown user";
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
