package dev.relaybot.discord;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Recent requests that failed signature checks. Kept in memory on purpose: writing junk traffic
 * to the database would let anyone fill it up. Shown on the dashboard so rejections are visible.
 */
@Component
public class RejectionLog {

    public record Rejection(Instant at, String reason, String remoteAddress) {
    }

    private static final int KEEP = 50;

    private final Deque<Rejection> recent = new ArrayDeque<>();
    private final AtomicLong total = new AtomicLong();

    public synchronized void record(String reason, String remoteAddress) {
        total.incrementAndGet();
        recent.addFirst(new Rejection(Instant.now(), reason, remoteAddress));
        while (recent.size() > KEEP) {
            recent.removeLast();
        }
    }

    public synchronized List<Rejection> recent() {
        return new ArrayList<>(recent);
    }

    public long total() {
        return total.get();
    }
}
