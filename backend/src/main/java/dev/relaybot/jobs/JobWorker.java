package dev.relaybot.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.PermanentException;
import dev.relaybot.common.Redactor;
import dev.relaybot.common.RetryableException;
import dev.relaybot.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs queued jobs with retries and exponential backoff.
 *
 * <ul>
 *   <li>Retryable failure: rescheduled at 5s, 10s, 20s… (capped at 10 min, with jitter), or at
 *       Discord's Retry-After on a 429.</li>
 *   <li>Permanent failure or attempts exhausted: marked FAILED, shown on the dashboard, and can be
 *       retried by the admin.</li>
 *   <li>Crash mid-job: the job stays RUNNING and is reclaimed on restart or after the stuck timeout.</li>
 * </ul>
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final JobRepository repository;
    private final JobQueue queue;
    private final JobSignal signal;
    private final Map<String, JobHandler> handlers;
    private final TransactionTemplate tx;
    private final ActivityService activity;
    private final ObjectMapper json;
    private final Clock clock;
    private final int batchSize;
    private final Duration stuckAfter;
    private final Duration sweepInterval;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile Instant lastSweep = Instant.EPOCH;

    public JobWorker(JobRepository repository, JobQueue queue, JobSignal signal, List<JobHandler> handlers,
                     TransactionTemplate tx, ActivityService activity, ObjectMapper json, Clock clock, AppProperties props) {
        this.repository = repository;
        this.queue = queue;
        this.signal = signal;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.tx = tx;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
        this.batchSize = props.jobs().batchSize();
        this.stuckAfter = Duration.ofSeconds(props.jobs().stuckAfterSeconds());
        this.sweepInterval = Duration.ofMinutes(props.jobs().safetySweepMinutes());
    }

    /** Single-instance deployment: anything RUNNING at startup was interrupted by the restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        try {
            int released = repository.releaseAbandoned();
            if (released > 0) {
                log.info("re-queued {} job(s) interrupted by a restart", released);
            }
        } catch (DataAccessException e) {
            log.warn("could not re-queue interrupted jobs yet: {}", e.getClass().getSimpleName());
        }
        signal.wakeNow();
    }

    @Scheduled(fixedDelayString = "${relaybot.jobs.poll-interval-ms}")
    public void tick() {
        Instant now = clock.instant();
        boolean sweepDue = Duration.between(lastSweep, now).compareTo(sweepInterval) >= 0;
        if (!signal.isDue(now) && !sweepDue) {
            return;
        }
        signal.clear();
        if (sweepDue) {
            lastSweep = now;
        }
        try {
            List<Job> claimed;
            do {
                claimed = repository.claimDue(batchSize, stuckAfter);
                if (claimed.isEmpty()) {
                    break;
                }
                List<Callable<Void>> tasks = claimed.stream().<Callable<Void>>map(job -> () -> {
                    run(job);
                    return null;
                }).toList();
                executor.invokeAll(tasks);
            } while (claimed.size() == batchSize);
            repository.nextDueAt().ifPresent(signal::wakeAt);
        } catch (DataAccessException e) {
            log.warn("job poll failed, retrying in 10s: {}", e.getClass().getSimpleName());
            signal.wakeAt(now.plusSeconds(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(Job job) {
        try (MDC.MDCCloseable a = MDC.putCloseable("jobId", String.valueOf(job.id()));
             MDC.MDCCloseable b = MDC.putCloseable("jobType", job.type());
             MDC.MDCCloseable c = MDC.putCloseable("guildId", job.guildId());
             MDC.MDCCloseable d = MDC.putCloseable("interactionId", job.interactionId())) {

            JobHandler handler = handlers.get(job.type());
            if (handler == null) {
                fail(job, "No handler registered for job type " + job.type());
                return;
            }
            try {
                JobContext context = new JobContext(job, json);
                handler.handle(job, context);
                tx.executeWithoutResult(status -> {
                    for (JobContext.FollowUp f : context.followUps()) {
                        queue.enqueue(f.type(), f.refs(), f.payload(), f.maxAttempts());
                    }
                    repository.markDone(job.id());
                });
                log.info("job done (attempt {}/{})", job.attempts(), job.maxAttempts());
            } catch (PermanentException e) {
                fail(job, e.getMessage());
            } catch (RetryableException e) {
                retryOrFail(job, e.getMessage(), e.retryAfter());
            } catch (DataAccessException e) {
                retryOrFail(job, "Database unavailable (" + e.getClass().getSimpleName() + ")", null);
            } catch (RuntimeException e) {
                log.error("unexpected error in job: {}", Redactor.redact(String.valueOf(e)));
                retryOrFail(job, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
            }
        }
    }

    private void retryOrFail(Job job, String error, Duration retryAfter) {
        String safe = Redactor.redact(error);
        if (job.isLastAttempt()) {
            fail(job, safe + " (gave up after " + job.attempts() + " attempts)");
            return;
        }
        Duration delay = retryAfter != null ? retryAfter.plusMillis(250) : backoff(job.attempts());
        Instant next = clock.instant().plus(delay);
        try {
            repository.markRetry(job.id(), next, safe);
        } catch (DataAccessException e) {
            log.warn("could not reschedule job; stuck-job recovery will pick it up");
            return;
        }
        signal.wakeAt(next);
        activity.warn(job.refs(), "job.retry", String.format("%s failed (attempt %d of %d): %s. Retrying in %ds.",
                JobTypes.label(job.type()), job.attempts(), job.maxAttempts(), safe, Math.max(1, delay.toSeconds())));
    }

    private void fail(Job job, String error) {
        String safe = Redactor.redact(error);
        try {
            repository.markFailed(job.id(), safe);
        } catch (DataAccessException e) {
            log.warn("could not mark job failed; stuck-job recovery will pick it up");
            return;
        }
        activity.error(job.refs(), "job.failed", JobTypes.label(job.type()) + " failed: " + safe);
    }

    static Duration backoff(int attempt) {
        long seconds = 5L << Math.min(Math.max(attempt - 1, 0), 10);
        long capped = Math.min(seconds * 1000, MAX_BACKOFF.toMillis());
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis((long) (capped * jitter));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
