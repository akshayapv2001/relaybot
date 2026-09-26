package dev.relaybot.guild;

import dev.relaybot.activity.ActivityService;
import dev.relaybot.ai.GroqClient;
import dev.relaybot.common.Redactor;
import dev.relaybot.common.Refs;
import dev.relaybot.common.SecretBox;
import dev.relaybot.discord.DiscordApi;
import dev.relaybot.mirror.MirrorClient;
import dev.relaybot.mirror.MirrorTarget;
import dev.relaybot.report.KeywordRule;
import dev.relaybot.report.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server configuration from the dashboard. The mirror URL is write-only: it is never returned. */
@RestController
@Validated
@RequestMapping("/api/guilds")
public class GuildController {

    public record GuildView(String id, String name, String postChannelId, boolean reportEnabled, boolean statusEnabled,
                            boolean aiEnabled, boolean ephemeralReplies, Priority defaultPriority, Priority mirrorMinPriority,
                            boolean mirrorConfigured, String mirrorKind, boolean aiAvailable, Instant connectedAt,
                            List<RuleView> rules) {
    }

    public record RuleView(@NotBlank @Size(max = 50) String keyword, @NotNull Priority priority) {
    }

    public record MirrorRequest(@NotBlank @Size(max = 500) String url) {
    }

    private final GuildRepository guilds;
    private final DiscordApi discord;
    private final SecretBox secrets;
    private final MirrorClient mirror;
    private final GroqClient groq;
    private final ActivityService activity;

    public GuildController(GuildRepository guilds, DiscordApi discord, SecretBox secrets, MirrorClient mirror,
                           GroqClient groq, ActivityService activity) {
        this.guilds = guilds;
        this.discord = discord;
        this.secrets = secrets;
        this.mirror = mirror;
        this.groq = groq;
        this.activity = activity;
    }

    @GetMapping
    public List<GuildView> list() {
        return guilds.findAll().stream().map(g -> view(g, List.of())).toList();
    }

    @GetMapping("/{id}")
    public GuildView get(@PathVariable String id) {
        Guild g = require(id);
        return view(g, guilds.rules(id).stream().map(r -> new RuleView(r.keyword(), r.priority())).toList());
    }

    @GetMapping("/{id}/channels")
    public List<DiscordApi.Channel> channels(@PathVariable String id) {
        require(id);
        return discord.listTextChannels(id);
    }

    @PutMapping("/{id}/settings")
    public GuildView updateSettings(@PathVariable String id, @Valid @RequestBody GuildSettings settings) {
        require(id);
        if (settings.postChannelId() != null
                && discord.listTextChannels(id).stream().noneMatch(c -> c.id().equals(settings.postChannelId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That channel isn't a text channel in this server.");
        }
        guilds.updateSettings(id, settings);
        activity.info(Refs.guild(id), "settings.updated", "Command settings updated.");
        return get(id);
    }

    @PutMapping("/{id}/rules")
    public GuildView replaceRules(@PathVariable String id, @RequestBody @Size(max = 50) List<@Valid RuleView> rules) {
        require(id);
        Set<String> seen = new HashSet<>();
        List<KeywordRule> clean = rules.stream()
                .map(r -> new KeywordRule(r.keyword().trim().toLowerCase(Locale.ROOT), r.priority()))
                .filter(r -> !r.keyword().isEmpty() && seen.add(r.keyword()))
                .toList();
        guilds.replaceRules(id, clean);
        activity.info(Refs.guild(id), "rules.updated", "Keyword rules updated (" + clean.size() + " rules).");
        return get(id);
    }

    @PutMapping("/{id}/mirror")
    public GuildView setMirror(@PathVariable String id, @Valid @RequestBody MirrorRequest body) {
        require(id);
        MirrorTarget target;
        try {
            target = MirrorTarget.parse(body.url());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        guilds.setMirror(id, secrets.encrypt(target.url()), target.kind().name());
        activity.info(Refs.guild(id), "mirror.updated", "Notification channel set (" + target.kind() + ").");
        return get(id);
    }

    @DeleteMapping("/{id}/mirror")
    public GuildView removeMirror(@PathVariable String id) {
        require(id);
        guilds.setMirror(id, null, null);
        activity.info(Refs.guild(id), "mirror.removed", "Notification channel removed.");
        return get(id);
    }

    @PostMapping("/{id}/mirror/test")
    public Map<String, String> testMirror(@PathVariable String id) {
        Guild g = require(id);
        if (!g.mirrorConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a notification channel first.");
        }
        try {
            MirrorTarget target = MirrorTarget.parse(secrets.decrypt(g.mirrorWebhookEnc()));
            mirror.send(target, "[" + g.name() + "] Test notification from RelayBot. Mirroring works.");
        } catch (RuntimeException e) {
            String reason = Redactor.redact(e.getMessage());
            activity.warn(Refs.guild(id), "mirror.test_failed", "Test notification failed: " + reason);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Test failed: " + reason);
        }
        activity.info(Refs.guild(id), "mirror.test_sent", "Test notification sent.");
        return Map.of("result", "sent");
    }

    /** Disconnect: removes the server's data and makes the bot leave (best effort). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(@PathVariable String id) {
        Guild g = require(id);
        try {
            discord.leaveGuild(id);
        } catch (RuntimeException e) {
            activity.warn(Refs.guild(id), "server.leave_failed", "Bot could not leave the server: " + e.getMessage());
        }
        guilds.delete(id);
        activity.info(Refs.none(), "server.disconnected", "Disconnected server \"" + g.name() + "\".");
        return ResponseEntity.noContent().build();
    }

    private Guild require(String id) {
        return guilds.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found"));
    }

    private GuildView view(Guild g, List<RuleView> rules) {
        return new GuildView(g.guildId(), g.name(), g.postChannelId(), g.reportEnabled(), g.statusEnabled(),
                g.aiEnabled(), g.ephemeralReplies(), g.defaultPriority(), g.mirrorMinPriority(),
                g.mirrorConfigured(), g.mirrorKind(), groq.enabled(), g.connectedAt(), rules);
    }
}
