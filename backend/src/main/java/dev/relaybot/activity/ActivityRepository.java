package dev.relaybot.activity;

import dev.relaybot.common.Db;
import dev.relaybot.common.Refs;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ActivityRepository {

    private static final RowMapper<Activity> MAPPER = (rs, n) -> new Activity(
            rs.getLong("id"), rs.getString("guild_id"), rs.getString("interaction_id"),
            Db.nullableLong(rs, "report_id"), Db.nullableLong(rs, "job_id"),
            rs.getString("level"), rs.getString("event"), rs.getString("message"), Db.instant(rs, "created_at"));

    private final JdbcClient jdbc;

    public ActivityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Activity insert(Refs refs, String level, String event, String message) {
        return jdbc.sql("""
                INSERT INTO activity_log (guild_id, interaction_id, report_id, job_id, level, event, message)
                VALUES (:guild, :interaction, :report, :job, :level, :event, :message)
                RETURNING *
                """)
                .param("guild", refs.guildId())
                .param("interaction", refs.interactionId())
                .param("report", refs.reportId())
                .param("job", refs.jobId())
                .param("level", level)
                .param("event", event)
                .param("message", message)
                .query(MAPPER).single();
    }

    public List<Activity> recent(String guildId, String level, int limit) {
        return jdbc.sql("""
                SELECT * FROM activity_log
                WHERE (CAST(:guild AS TEXT) IS NULL OR guild_id = :guild)
                  AND (CAST(:level AS TEXT) IS NULL OR level = :level)
                ORDER BY created_at DESC, id DESC LIMIT :limit
                """).param("guild", guildId).param("level", level).param("limit", limit).query(MAPPER).list();
    }

    public int countSince(String level, java.time.Instant since) {
        return jdbc.sql("SELECT COUNT(*) FROM activity_log WHERE level = :level AND created_at >= :since")
                .param("level", level).param("since", Db.ts(since)).query(Integer.class).single();
    }
}
