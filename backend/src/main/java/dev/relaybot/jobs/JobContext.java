package dev.relaybot.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.relaybot.common.PermanentException;
import dev.relaybot.common.Refs;

import java.util.ArrayList;
import java.util.List;

/**
 * Handed to a handler. Follow-up jobs are collected here and inserted in the same transaction
 * that marks this job DONE, so a step and its successors are never half-recorded.
 */
public final class JobContext {

    public record FollowUp(String type, Refs refs, Object payload, int maxAttempts) {
    }

    private final Job job;
    private final ObjectMapper json;
    private final List<FollowUp> followUps = new ArrayList<>();

    JobContext(Job job, ObjectMapper json) {
        this.job = job;
        this.json = json;
    }

    public <T> T payload(Class<T> type) {
        try {
            return json.readValue(job.payload(), type);
        } catch (JsonProcessingException e) {
            throw new PermanentException("Job payload could not be read");
        }
    }

    public void followUp(String type, Object payload, int maxAttempts) {
        followUps.add(new FollowUp(type, job.refs().withJob(null), payload, maxAttempts));
    }

    List<FollowUp> followUps() {
        return followUps;
    }
}
