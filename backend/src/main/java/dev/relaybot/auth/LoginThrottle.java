package dev.relaybot.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Slows password guessing: at most 10 failed logins per client address per 15 minutes. */
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Window(Instant start, int failures) {
    }

    private final Map<String, Window> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String client) {
        Window w = failures.get(client);
        return w != null && w.failures() >= MAX_FAILURES && Instant.now().isBefore(w.start().plus(WINDOW));
    }

    public void recordFailure(String client) {
        Instant now = Instant.now();
        failures.compute(client, (k, w) -> (w == null || now.isAfter(w.start().plus(WINDOW)))
                ? new Window(now, 1) : new Window(w.start(), w.failures() + 1));
        if (failures.size() > 10_000) {
            failures.clear();   // bounded memory under a flood
        }
    }

    public void reset(String client) {
        failures.remove(client);
    }
}
