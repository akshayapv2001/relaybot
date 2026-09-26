package dev.relaybot.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {

    @Test
    void growsExponentiallyAndIsCapped() {
        assertThat(JobWorker.backoff(1)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(6));
        assertThat(JobWorker.backoff(3)).isBetween(Duration.ofSeconds(16), Duration.ofSeconds(24));
        assertThat(JobWorker.backoff(20)).isLessThanOrEqualTo(Duration.ofMinutes(12));
    }
}
