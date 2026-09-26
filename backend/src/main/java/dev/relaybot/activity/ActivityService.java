package dev.relaybot.activity;

import dev.relaybot.common.Redactor;
import dev.relaybot.common.Refs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Records a human-readable event in the activity log (shown live on the dashboard) and writes the
 * same event as a structured log line. If called inside a transaction, the row commits or rolls back
 * with it, and the live push happens only after commit (see ActivityStream).
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository repository;
    private final ApplicationEventPublisher events;

    public ActivityService(ActivityRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public void info(Refs refs, String event, String message) {
        record(refs, "INFO", event, message);
    }

    public void warn(Refs refs, String event, String message) {
        record(refs, "WARN", event, message);
    }

    public void error(Refs refs, String event, String message) {
        record(refs, "ERROR", event, message);
    }

    private void record(Refs refs, String level, String event, String message) {
        String safe = Redactor.redact(message);
        logStructured(refs, level, event, safe);
        try {
            Activity saved = repository.insert(refs, level, event, safe);
            events.publishEvent(saved);
        } catch (RuntimeException e) {
            // The activity log is observability, not the source of truth; never let it break the real work.
            log.warn("could not write activity row: {}", Redactor.redact(e.getMessage()));
        }
    }

    private static void logStructured(Refs refs, String level, String event, String message) {
        try (MDC.MDCCloseable a = MDC.putCloseable("event", event);
             MDC.MDCCloseable b = MDC.putCloseable("guildId", refs.guildId());
             MDC.MDCCloseable c = MDC.putCloseable("interactionId", refs.interactionId());
             MDC.MDCCloseable d = MDC.putCloseable("reportId", refs.reportId() == null ? null : refs.reportId().toString());
             MDC.MDCCloseable e = MDC.putCloseable("jobId", refs.jobId() == null ? null : refs.jobId().toString())) {
            switch (level) {
                case "ERROR" -> log.error(message);
                case "WARN" -> log.warn(message);
                default -> log.info(message);
            }
        }
    }
}
