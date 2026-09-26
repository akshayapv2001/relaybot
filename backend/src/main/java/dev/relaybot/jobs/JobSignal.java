package dev.relaybot.jobs;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tells the worker when it next needs to look at the database.
 *
 * <p>Why not just poll every second? Neon's free Postgres scales to zero when idle, and a query
 * every second would keep it awake around the clock and burn the free compute allowance. Instead
 * the worker polls only when a job was just enqueued, a retry is due, or on an hourly safety sweep.
 * Signals are merged with {@code min}, so a wake-up can't be lost while the worker is busy.
 */
@Component
public class JobSignal {

    private final AtomicReference<Instant> nextDue = new AtomicReference<>(Instant.EPOCH);

    public void wakeNow() {
        wakeAt(Instant.EPOCH);
    }

    public void wakeAt(Instant at) {
        nextDue.accumulateAndGet(at, (current, candidate) -> candidate.isBefore(current) ? candidate : current);
    }

    boolean isDue(Instant now) {
        return !nextDue.get().isAfter(now);
    }

    /** Called at the start of a poll; any signal arriving after this is kept for the next tick. */
    void clear() {
        nextDue.set(Instant.MAX);
    }
}
