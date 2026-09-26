package dev.relaybot.discord;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.common.Redactor;
import dev.relaybot.common.Refs;
import dev.relaybot.config.AppProperties;
import dev.relaybot.guild.GuildRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * "Connect a server": sends the logged-in admin to Discord's add-bot screen, and handles the redirect
 * back. The random state value (kept in the admin's session) proves the callback belongs to a flow
 * this admin started, which blocks CSRF-style forged callbacks.
 */
@RestController
public class ConnectController {

    private static final Logger log = LoggerFactory.getLogger(ConnectController.class);
    private static final String STATE_KEY = "discord_oauth_state";

    /** View Channel (1024) + Send Messages (2048) + Embed Links (16384) + Read Message History (65536). */
    private static final long BOT_PERMISSIONS = 1024 + 2048 + 16384 + 65536;

    private final AppProperties props;
    private final DiscordApi discord;
    private final GuildRepository guilds;
    private final ActivityService activity;
    private final SecureRandom random = new SecureRandom();

    public ConnectController(AppProperties props, DiscordApi discord, GuildRepository guilds, ActivityService activity) {
        this.props = props;
        this.discord = discord;
        this.guilds = guilds;
        this.activity = activity;
    }

    @GetMapping("/api/discord/connect")
    public ResponseEntity<Void> start(HttpSession session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(STATE_KEY, state);

        URI authorize = UriComponentsBuilder.fromUriString("https://discord.com/oauth2/authorize")
                .queryParam("client_id", props.discord().applicationId())
                .queryParam("scope", "bot applications.commands")
                .queryParam("permissions", BOT_PERMISSIONS)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", state)
                .encode().build().toUri();
        return redirect(authorize.toString());
    }

    @GetMapping("/api/discord/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpSession session) {
        Object expected = session.getAttribute(STATE_KEY);
        session.removeAttribute(STATE_KEY);

        if (error != null) {
            return redirect("/servers?connect=cancelled");
        }
        if (code == null || state == null || !(expected instanceof String s)
                || !MessageDigest.isEqual(s.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            return redirect("/servers?connect=invalid");
        }
        try {
            DiscordApi.InstalledGuild guild = discord.exchangeInstallCode(code, redirectUri());
            guilds.upsertConnected(guild.id(), guild.name());
            activity.info(Refs.guild(guild.id()), "server.connected", "Connected server \"" + guild.name() + "\".");
            return redirect("/servers/" + guild.id() + "?connect=ok");
        } catch (RuntimeException e) {
            log.warn("server connect failed: {}", Redactor.redact(e.getMessage()));
            return redirect("/servers?connect=failed");
        }
    }

    private String redirectUri() {
        return props.baseUrl() + "/api/discord/oauth/callback";
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
