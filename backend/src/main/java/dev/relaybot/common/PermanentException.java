package dev.relaybot.common;

/** A failure that retrying cannot fix (4xx, missing permissions, expired token). The job fails immediately. */
public class PermanentException extends RuntimeException {

    public PermanentException(String message) {
        super(message);
    }
}
