package dev.relaybot.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.relaybot.common.Refs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class JobQueue {

    private final JobRepository repository;
    private final JobSignal signal;
    private final ObjectMapper json;

    public JobQueue(JobRepository repository, JobSignal signal, ObjectMapper json) {
        this.repository = repository;
        this.signal = signal;
        this.json = json;
    }

    /** Joins the caller's transaction. The worker is woken only after commit, when the row is visible. */
    public long enqueue(String type, Refs refs, Object payload, int maxAttempts) {
        long id = repository.insert(type, refs, write(payload), maxAttempts);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.wakeNow();
                }
            });
        } else {
            signal.wakeNow();
        }
        return id;
    }

    private String write(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Job payload is not serialisable", e);
        }
    }
}
