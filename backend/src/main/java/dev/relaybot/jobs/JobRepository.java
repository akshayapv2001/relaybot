package dev.relaybot.jobs;

import dev.relaybot.common.Db;
import dev.relaybot.common.Refs;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JobRepository {

    /** What the dashboard sees. Deliberately excludes the payload (it can hold an interaction token). */
    public record JobView(long id, String type, String guildId, Long reportId, String status, int attempts,
                          int maxAttempts, Instant nextRunAt, String lastError, Instant createdAt, Instant updatedAt) {
    }

    private static final RowMapper<Job> JOB = (rs, n) -> new Job(
            rs.getLong("id"), rs.getString("type"), rs.getString("guild_id"), rs.getString("interaction_id"),
            Db.nullableLong(rs, "report_id"), rs.getString("payload"), rs.getInt("attempts"), rs.getInt("max_attempts"));

    private static final RowMapper<JobView> VIEW = (rs, n) -> new JobView(
            rs.getLong("id"), rs.getString("type"), rs.getString("guild_id"), Db.nullableLong(rs, "report_id"),
            rs.getString("status"), rs.getInt("attempts"), rs.getInt("max_attempts"), Db.instant(rs, "next_run_at"),
            rs.getString("last_error"), Db.instant(rs, "created_at"), Db.instant(rs, "updated_at"));

    private final JdbcClient jdbc;

    public JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String type, Refs refs, String payload, int maxAttempts) {
        return jdbc.sql("""
                INSERT INTO jobs (type, guild_id, interaction_id, report_id, payload, max_attempts)
                VALUES (:type, :guild, :interaction, :report, :payload, :max)
                RETURNING id
                """)
                .param("type", type)
                .param("guild", refs.guildId())
                .param("interaction", refs.interactionId())
                .param("report", refs.reportId())
                .param("payload", payload)
                .param("max", maxAttempts)
                .query(Long.class).single();
    }

    /**
     * Atomically claims due jobs. FOR UPDATE SKIP LOCKED means two workers can never claim the
     * same job. RUNNING jobs whose lock is older than {@code stuckAfter} are reclaimed: that is a
     * worker that died mid-job.
     */
    public List<Job> claimDue(int limit, Duration stuckAfter) {
        return jdbc.sql("""
                UPDATE jobs SET status = 'RUNNING', locked_at = now(), attempts = attempts + 1, updated_at = now()
                WHERE id IN (
                    SELECT id FROM jobs
                    WHERE (status = 'PENDING' AND next_run_at <= now())
                       OR (status = 'RUNNING' AND locked_at < now() - make_interval(secs => :stuck))
                    ORDER BY next_run_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, type, guild_id, interaction_id, report_id, payload, attempts, max_attempts
                """)
                .param("stuck", (double) stuckAfter.toSeconds())
                .param("limit", limit)
                .query(JOB).list();
    }

    /** On startup (single instance), anything still RUNNING was interrupted by the restart. */
    public int releaseAbandoned() {
        return jdbc.sql("UPDATE jobs SET status = 'PENDING', next_run_at = now(), updated_at = now() WHERE status = 'RUNNING'")
                .update();
    }

    public void markDone(long id) {
        jdbc.sql("UPDATE jobs SET status = 'DONE', locked_at = NULL, last_error = NULL, updated_at = now() WHERE id = :id")
                .param("id", id).update();
    }

    public void markRetry(long id, Instant nextRunAt, String error) {
        jdbc.sql("""
                UPDATE jobs SET status = 'PENDING', locked_at = NULL, next_run_at = :next, last_error = :err, updated_at = now()
                WHERE id = :id
                """).param("next", Db.ts(nextRunAt)).param("err", error).param("id", id).update();
    }

    public void markFailed(long id, String error) {
        jdbc.sql("UPDATE jobs SET status = 'FAILED', locked_at = NULL, last_error = :err, updated_at = now() WHERE id = :id")
                .param("err", error).param("id", id).update();
    }

    /** Manual retry from the dashboard: gives the job three more attempts. */
    public boolean retryNow(long id) {
        return jdbc.sql("""
                UPDATE jobs SET status = 'PENDING', next_run_at = now(), max_attempts = attempts + 3, updated_at = now()
                WHERE id = :id AND status = 'FAILED'
                """).param("id", id).update() == 1;
    }

    public Optional<Instant> nextDueAt() {
        return jdbc.sql("SELECT MIN(next_run_at) AS t FROM jobs WHERE status = 'PENDING'")
                .query((rs, n) -> Db.instant(rs, "t"))
                .list().stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public List<JobView> recent(String status, String guildId, int limit) {
        return jdbc.sql("""
                SELECT id, type, guild_id, report_id, status, attempts, max_attempts, next_run_at, last_error, created_at, updated_at
                FROM jobs
                WHERE (CAST(:status AS TEXT) IS NULL OR status = :status)
                  AND (CAST(:guild AS TEXT) IS NULL OR guild_id = :guild)
                ORDER BY updated_at DESC LIMIT :limit
                """).param("status", status).param("guild", guildId).param("limit", limit).query(VIEW).list();
    }

    public int countByStatus(String status) {
        return jdbc.sql("SELECT COUNT(*) FROM jobs WHERE status = :s").param("s", status).query(Integer.class).single();
    }
}
