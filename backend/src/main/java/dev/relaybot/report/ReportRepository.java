package dev.relaybot.report;

import dev.relaybot.common.Db;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReportRepository {

    private static final RowMapper<Report> MAPPER = (rs, n) -> new Report(
            rs.getLong("id"),
            rs.getString("guild_id"),
            rs.getString("interaction_id"),
            rs.getString("channel_id"),
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("text"),
            Priority.parseOrNull(rs.getString("priority")),
            rs.getString("priority_source"),
            rs.getString("matched_keyword"),
            rs.getString("ai_status"),
            rs.getString("ai_summary"),
            rs.getString("ai_category"),
            rs.getString("status"),
            rs.getString("acted_by"),
            rs.getString("post_channel_id"),
            rs.getString("channel_message_id"),
            Db.instant(rs, "created_at"));

    public record NewReport(String guildId, String interactionId, String channelId, String userId, String username, String text) {
    }

    public record OpenSummary(Map<Priority, Integer> openByPriority, Instant lastReportAt) {
    }

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long create(NewReport r) {
        return jdbc.sql("""
                INSERT INTO reports (guild_id, interaction_id, channel_id, user_id, username, text)
                VALUES (:guild, :interaction, :channel, :userId, :username, :text)
                RETURNING id
                """)
                .param("guild", r.guildId())
                .param("interaction", r.interactionId())
                .param("channel", r.channelId())
                .param("userId", r.userId())
                .param("username", r.username())
                .param("text", r.text())
                .query(Long.class)
                .single();
    }

    public Optional<Report> find(long id) {
        return jdbc.sql("SELECT * FROM reports WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    public List<Report> recent(String guildId, int limit) {
        return jdbc.sql("""
                SELECT * FROM reports
                WHERE (CAST(:guild AS TEXT) IS NULL OR guild_id = :guild)
                ORDER BY created_at DESC LIMIT :limit
                """).param("guild", guildId).param("limit", limit).query(MAPPER).list();
    }

    public void applyTriage(long id, RuleEngine.Decision decision, String aiStatus, String aiSummary, String aiCategory) {
        jdbc.sql("""
                UPDATE reports SET priority = :pri, priority_source = :src, matched_keyword = :kw,
                       ai_status = :aiStatus, ai_summary = :summary, ai_category = :category, updated_at = now()
                WHERE id = :id
                """)
                .param("pri", decision.priority().name())
                .param("src", decision.source())
                .param("kw", decision.matchedKeyword())
                .param("aiStatus", aiStatus)
                .param("summary", aiSummary)
                .param("category", aiCategory)
                .param("id", id)
                .update();
    }

    public void setChannelMessage(long id, String channelId, String messageId) {
        jdbc.sql("UPDATE reports SET post_channel_id = :c, channel_message_id = :m, updated_at = now() WHERE id = :id")
                .param("c", channelId).param("m", messageId).param("id", id).update();
    }

    /** Conditional update: returns false if the report was not in an allowed state (so clicks are idempotent). */
    public boolean acknowledge(long id, String by) {
        return jdbc.sql("""
                UPDATE reports SET status = 'ACKNOWLEDGED', acted_by = :by, updated_at = now()
                WHERE id = :id AND status IN ('OPEN', 'ESCALATED')
                """).param("by", by).param("id", id).update() == 1;
    }

    public boolean escalate(long id, String by) {
        return jdbc.sql("""
                UPDATE reports SET status = 'ESCALATED', priority = 'HIGH', acted_by = :by, updated_at = now()
                WHERE id = :id AND status = 'OPEN'
                """).param("by", by).param("id", id).update() == 1;
    }

    public OpenSummary openSummary(String guildId) {
        Map<Priority, Integer> counts = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            counts.put(p, 0);
        }
        jdbc.sql("""
                SELECT COALESCE(priority, 'LOW') AS priority, COUNT(*) AS n FROM reports
                WHERE guild_id = :guild AND status <> 'ACKNOWLEDGED' GROUP BY 1
                """).param("guild", guildId)
                .query(rs -> {
                    Priority p = Priority.parseOrNull(rs.getString("priority"));
                    if (p != null) {
                        counts.put(p, rs.getInt("n"));
                    }
                });
        Instant last = jdbc.sql("SELECT MAX(created_at) AS t FROM reports WHERE guild_id = :guild")
                .param("guild", guildId)
                .query((rs, n) -> Db.instant(rs, "t"))
                .list().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new OpenSummary(counts, last);
    }
}
