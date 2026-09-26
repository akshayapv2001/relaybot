package dev.relaybot.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes new activity to open dashboards over Server-Sent Events. Events are delivered after the
 * surrounding transaction commits, so the dashboard never shows something that was rolled back.
 * Nothing here touches the database, so an idle dashboard does not keep Neon awake.
 */
@Component
public class ActivityStream {

    private static final Logger log = LoggerFactory.getLogger(ActivityStream.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onActivity(Activity activity) {
        broadcast(SseEmitter.event().name("activity").data(activity));
    }

    /** Keeps proxies from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        broadcast(SseEmitter.event().comment("keep-alive"));
    }

    private void broadcast(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("dropped SSE subscriber: {}", e.getClass().getSimpleName());
            }
        }
    }
}
