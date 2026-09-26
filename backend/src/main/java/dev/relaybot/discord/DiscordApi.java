package dev.relaybot.discord;

import com.fasterxml.jackson.databind.JsonNode;
import dev.relaybot.common.HttpCalls;
import dev.relaybot.common.PermanentException;
import dev.relaybot.config.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Thin client for the Discord REST calls RelayBot makes. */
@Component
public class DiscordApi {

    public record Channel(String id, String name, int position) {
    }

    public record InstalledGuild(String id, String name) {
    }

    private final RestClient http;
    private final AppProperties props;

    public DiscordApi(@Qualifier("discord") RestClient http, AppProperties props) {
        this.http = http;
        this.props = props;
    }

    /** Edits the deferred response (or, for a button click, the message the button is on). Token lives 15 minutes. */
    public void editOriginalResponse(String interactionToken, Map<String, Object> message) {
        try {
            HttpCalls.execute("Discord follow-up",
                    http.patch().uri("/webhooks/{app}/{token}/messages/@original", props.discord().applicationId(), interactionToken)
                            .contentType(MediaType.APPLICATION_JSON).body(message),
                    Void.class);
        } catch (PermanentException e) {
            if (e.getMessage().contains("HTTP 404") || e.getMessage().contains("HTTP 401")) {
                throw new PermanentException("Discord follow-up failed: the interaction token expired (15-minute limit)");
            }
            throw e;
        }
    }

    /** Posts a bot message to a channel and returns the new message id. */
    public String createChannelMessage(String channelId, Map<String, Object> message) {
        try {
            JsonNode created = HttpCalls.execute("Discord channel post",
                    http.post().uri("/channels/{id}/messages", channelId)
                            .header(HttpHeaders.AUTHORIZATION, botAuth())
                            .contentType(MediaType.APPLICATION_JSON).body(message),
                    JsonNode.class);
            return created.path("id").asText();
        } catch (PermanentException e) {
            if (e.getMessage().contains("HTTP 403")) {
                throw new PermanentException("The bot can't post in the configured channel. Give it View Channel, Send Messages and Embed Links there.");
            }
            throw e;
        }
    }

    public List<Channel> listTextChannels(String guildId) {
        JsonNode channels = HttpCalls.execute("Discord channel list",
                http.get().uri("/guilds/{id}/channels", guildId).header(HttpHeaders.AUTHORIZATION, botAuth()),
                JsonNode.class);
        List<Channel> result = new ArrayList<>();
        for (JsonNode c : channels) {
            int type = c.path("type").asInt(-1);
            if (type == 0 || type == 5) {   // text and announcement channels
                result.add(new Channel(c.path("id").asText(), c.path("name").asText(), c.path("position").asInt()));
            }
        }
        result.sort((a, b) -> Integer.compare(a.position(), b.position()));
        return result;
    }

    /**
     * Completes the "add bot to server" OAuth flow. With scope=bot the token response includes
     * the guild the bot was added to; we only need that, so the user access token is discarded.
     */
    public InstalledGuild exchangeInstallCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        JsonNode token = HttpCalls.execute("Discord OAuth token exchange",
                http.post().uri("/oauth2/token")
                        .headers(h -> h.setBasicAuth(props.discord().applicationId(), props.discord().clientSecret()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form),
                JsonNode.class);
        JsonNode guild = token.path("guild");
        if (guild.isMissingNode() || guild.path("id").asText().isBlank()) {
            throw new PermanentException("Discord did not return a server. Make sure the bot scope was granted.");
        }
        return new InstalledGuild(guild.path("id").asText(), guild.path("name").asText());
    }

    /** Bulk overwrite: idempotent, safe to run on every startup. */
    public void registerGlobalCommands(List<Map<String, Object>> commands) {
        HttpCalls.execute("Discord command registration",
                http.put().uri("/applications/{app}/commands", props.discord().applicationId())
                        .header(HttpHeaders.AUTHORIZATION, botAuth())
                        .contentType(MediaType.APPLICATION_JSON).body(commands),
                Void.class);
    }

    public void leaveGuild(String guildId) {
        HttpCalls.execute("Discord leave server",
                http.delete().uri("/users/@me/guilds/{id}", guildId).header(HttpHeaders.AUTHORIZATION, botAuth()),
                Void.class);
    }

    private String botAuth() {
        return "Bot " + props.discord().botToken();
    }
}
