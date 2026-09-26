package dev.relaybot.guild;

import dev.relaybot.common.Db;
import dev.relaybot.report.KeywordRule;
import dev.relaybot.report.Priority;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class GuildRepository {

    private static final RowMapper<Guild> MAPPER = (rs, n) -> new Guild(
            rs.getString("guild_id"),
            rs.getString("name"),
            rs.getString("post_channel_id"),
            rs.getBoolean("report_enabled"),
            rs.getBoolean("status_enabled"),
            rs.getBoolean("ai_enabled"),
            rs.getBoolean("ephemeral_replies"),
            Priority.valueOf(rs.getString("default_priority")),
            Priority.valueOf(rs.getString("mirror_min_priority")),
            rs.getString("mirror_webhook_enc"),
            rs.getString("mirror_kind"),
            Db.instant(rs, "connected_at"));

    private final JdbcClient jdbc;

    public GuildRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Guild> find(String guildId) {
        return jdbc.sql("SELECT * FROM guilds WHERE guild_id = :id").param("id", guildId).query(MAPPER).optional();
    }

    public List<Guild> findAll() {
        return jdbc.sql("SELECT * FROM guilds ORDER BY name").query(MAPPER).list();
    }

    /** Called after the OAuth install. Re-connecting keeps existing settings. */
    public void upsertConnected(String guildId, String name) {
        jdbc.sql("""
                INSERT INTO guilds (guild_id, name) VALUES (:id, :name)
                ON CONFLICT (guild_id) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
                """).param("id", guildId).param("name", name).update();
    }

    public void updateSettings(String guildId, GuildSettings s) {
        jdbc.sql("""
                UPDATE guilds SET post_channel_id = :channel, report_enabled = :report, status_enabled = :status,
                       ai_enabled = :ai, ephemeral_replies = :eph, default_priority = :defPri,
                       mirror_min_priority = :mirPri, updated_at = now()
                WHERE guild_id = :id
                """)
                .param("channel", s.postChannelId())
                .param("report", s.reportEnabled())
                .param("status", s.statusEnabled())
                .param("ai", s.aiEnabled())
                .param("eph", s.ephemeralReplies())
                .param("defPri", s.defaultPriority().name())
                .param("mirPri", s.mirrorMinPriority().name())
                .param("id", guildId)
                .update();
    }

    public void setMirror(String guildId, String encryptedUrl, String kind) {
        jdbc.sql("UPDATE guilds SET mirror_webhook_enc = :enc, mirror_kind = :kind, updated_at = now() WHERE guild_id = :id")
                .param("enc", encryptedUrl).param("kind", kind).param("id", guildId).update();
    }

    public void delete(String guildId) {
        jdbc.sql("DELETE FROM guilds WHERE guild_id = :id").param("id", guildId).update();
    }

    public List<KeywordRule> rules(String guildId) {
        return jdbc.sql("SELECT keyword, priority FROM keyword_rules WHERE guild_id = :id ORDER BY position")
                .param("id", guildId)
                .query((rs, n) -> new KeywordRule(rs.getString("keyword"), Priority.valueOf(rs.getString("priority"))))
                .list();
    }

    @Transactional
    public void replaceRules(String guildId, List<KeywordRule> rules) {
        jdbc.sql("DELETE FROM keyword_rules WHERE guild_id = :id").param("id", guildId).update();
        int position = 0;
        for (KeywordRule rule : rules) {
            jdbc.sql("INSERT INTO keyword_rules (guild_id, keyword, priority, position) VALUES (:id, :kw, :pri, :pos)")
                    .param("id", guildId).param("kw", rule.keyword()).param("pri", rule.priority().name())
                    .param("pos", position++).update();
        }
    }
}
