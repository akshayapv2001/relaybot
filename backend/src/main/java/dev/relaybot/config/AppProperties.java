package dev.relaybot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All configuration, bound from environment variables (see application.yml and .env.example).
 * Validated at startup so a missing secret fails fast instead of failing on the first request.
 */
@Validated
@ConfigurationProperties(prefix = "relaybot")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        @Valid @NotNull Discord discord,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Groq groq,
        @Valid @NotNull Security security,
        @Valid @NotNull Jobs jobs) {

    public record Discord(
            @NotBlank String applicationId,
            @NotBlank String publicKey,
            @NotBlank String botToken,
            @NotBlank String clientSecret,
            boolean registerCommands,
            @NotBlank String apiBase,
            long maxTimestampSkewSeconds) {
    }

    public record Admin(@NotBlank String username, @NotBlank String password) {
    }

    public record Groq(String apiKey, @NotBlank String model, @NotBlank String baseUrl) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Security(@NotBlank String encryptionKey) {
    }

    public record Jobs(long pollIntervalMs, int batchSize, long stuckAfterSeconds, long safetySweepMinutes) {
    }

    /** Public URL without a trailing slash. */
    public String baseUrl() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }
}
