package dev.relaybot.common;

import java.time.Duration;

/** A downstream failure that may succeed later (timeouts, 5xx, 429). The job is retried with backoff. */
public class RetryableException extends RuntimeException {

    private final Duration retryAfter;

    public RetryableException(String message) {
        this(message, null);
    }

    public RetryableException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** Server-requested delay (e.g. Discord's Retry-After), or null to use normal backoff. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
