package dev.relaybot.config;

import dev.relaybot.common.SecretBox;
import dev.relaybot.discord.SignatureVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class BeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SignatureVerifier signatureVerifier(AppProperties props, Clock clock) {
        return new SignatureVerifier(props.discord().publicKey(), props.discord().maxTimestampSkewSeconds(), clock);
    }

    @Bean
    SecretBox secretBox(AppProperties props) {
        return new SecretBox(props.security().encryptionKey());
    }

    /** Discord REST API. Every outbound call has a timeout: an unbounded call can stall the job worker. */
    @Bean
    @Qualifier("discord")
    RestClient discordRestClient(AppProperties props) {
        return RestClient.builder()
                .baseUrl(props.discord().apiBase())
                .requestFactory(requestFactory(Duration.ofSeconds(10)))
                .defaultHeader(HttpHeaders.USER_AGENT, "DiscordBot (https://github.com/relaybot, 1.0)")
                .build();
    }

    /** Groq and mirror webhooks. */
    @Bean
    @Qualifier("external")
    RestClient externalRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(12)))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
