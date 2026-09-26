package dev.relaybot.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Used by Render's health check and the keep-alive ping. Deliberately does not touch the database,
 * so pinging it keeps the web service warm without keeping Neon awake.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
