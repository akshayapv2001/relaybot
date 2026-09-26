package dev.relaybot.discord;

import dev.relaybot.common.Redactor;
import dev.relaybot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registers the slash commands on startup. Bulk overwrite is idempotent, so the commands in Discord
 * always match this code. Failure is logged, not fatal: the bot keeps serving existing commands.
 */
@Component
public class CommandRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);

    static final List<Map<String, Object>> COMMANDS = List.of(
            Map.of(
                    "name", "report",
                    "type", 1,
                    "description", "Report a problem to the team",
                    "contexts", List.of(0),            // guilds only
                    "integration_types", List.of(0),   // installed to a server
                    "options", List.of(Map.of(
                            "type", 3,
                            "name", "text",
                            "description", "What happened? Leave empty to open a form.",
                            "required", false,
                            "max_length", 1000))),
            Map.of(
                    "name", "status",
                    "type", 1,
                    "description", "Show open reports for this server",
                    "contexts", List.of(0),
                    "integration_types", List.of(0)));

    private final DiscordApi discord;
    private final AppProperties props;

    public CommandRegistrar(DiscordApi discord, AppProperties props) {
        this.discord = discord;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.discord().registerCommands()) {
            return;
        }
        try {
            discord.registerGlobalCommands(COMMANDS);
            log.info("registered slash commands: /report, /status");
        } catch (RuntimeException e) {
            log.warn("could not register slash commands: {}", Redactor.redact(e.getMessage()));
        }
    }
}
