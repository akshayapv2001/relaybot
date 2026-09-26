package dev.relaybot.discord;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InteractionRepository {

    public record NewInteraction(String id, String guildId, int type, String command, String userId, String username) {
    }

    private final JdbcClient jdbc;

    public InteractionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The dedup guard. Returns true only for the first delivery of an interaction id;
     * a repeat delivery inserts nothing and returns false, so no side effects run twice.
     */
    public boolean tryInsert(NewInteraction i) {
        return jdbc.sql("""
                INSERT INTO interactions (interaction_id, guild_id, interaction_type, command, user_id, username)
                VALUES (:id, :guild, :type, :command, :userId, :username)
                ON CONFLICT (interaction_id) DO NOTHING
                """)
                .param("id", i.id())
                .param("guild", i.guildId())
                .param("type", i.type())
                .param("command", i.command())
                .param("userId", i.userId())
                .param("username", i.username())
                .update() == 1;
    }

    public void setOutcome(String interactionId, String outcome) {
        jdbc.sql("UPDATE interactions SET outcome = :o WHERE interaction_id = :id")
                .param("o", outcome).param("id", interactionId).update();
    }
}
