package dev.relaybot.jobs;

/**
 * One step of work. Handlers throw RetryableException / PermanentException to control retries,
 * and should be safe to run more than once (a crash after the side effect but before the job is
 * marked done causes a re-run).
 */
public interface JobHandler {

    String type();

    void handle(Job job, JobContext context);
}
