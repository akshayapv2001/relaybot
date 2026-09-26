package dev.relaybot.web;

import dev.relaybot.activity.Activity;
import dev.relaybot.activity.ActivityRepository;
import dev.relaybot.activity.ActivityStream;
import dev.relaybot.discord.RejectionLog;
import dev.relaybot.jobs.JobRepository;
import dev.relaybot.jobs.JobSignal;
import dev.relaybot.report.Report;
import dev.relaybot.report.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-mostly endpoints behind the admin login: live log, reports, jobs and failures. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final Set<String> JOB_STATUSES = Set.of("PENDING", "RUNNING", "DONE", "FAILED");

    public record Overview(int jobsPending, int jobsFailed, int errorsLast24h, long rejectedRequests,
                           List<RejectionLog.Rejection> recentRejections) {
    }

    private final ActivityRepository activity;
    private final ActivityStream stream;
    private final ReportRepository reports;
    private final JobRepository jobs;
    private final JobSignal signal;
    private final RejectionLog rejections;

    public DashboardController(ActivityRepository activity, ActivityStream stream, ReportRepository reports,
                               JobRepository jobs, JobSignal signal, RejectionLog rejections) {
        this.activity = activity;
        this.stream = stream;
        this.reports = reports;
        this.jobs = jobs;
        this.signal = signal;
        this.rejections = rejections;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return new Overview(
                jobs.countByStatus("PENDING") + jobs.countByStatus("RUNNING"),
                jobs.countByStatus("FAILED"),
                activity.countSince("ERROR", Instant.now().minus(Duration.ofHours(24))),
                rejections.total(),
                rejections.recent().stream().limit(10).toList());
    }

    @GetMapping("/activity")
    public List<Activity> activity(@RequestParam(required = false) String guildId,
                                   @RequestParam(required = false) String level,
                                   @RequestParam(defaultValue = "100") int limit) {
        return activity.recent(blankToNull(guildId), blankToNull(level), clamp(limit));
    }

    @GetMapping(path = "/activity/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter activityStream() {
        return stream.subscribe();
    }

    @GetMapping("/reports")
    public List<Report> reports(@RequestParam(required = false) String guildId,
                                @RequestParam(defaultValue = "100") int limit) {
        return reports.recent(blankToNull(guildId), clamp(limit));
    }

    @GetMapping("/jobs")
    public List<JobRepository.JobView> jobs(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String guildId,
                                            @RequestParam(defaultValue = "100") int limit) {
        String s = blankToNull(status);
        if (s != null && !JOB_STATUSES.contains(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        return jobs.recent(s, blankToNull(guildId), clamp(limit));
    }

    @PostMapping("/jobs/{id}/retry")
    public Map<String, String> retry(@PathVariable long id) {
        if (!jobs.retryNow(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried.");
        }
        signal.wakeNow();
        return Map.of("result", "queued");
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
