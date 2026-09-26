package dev.relaybot.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Turns every outbound HTTP result into one of three outcomes: success, {@link RetryableException}
 * (timeouts, 5xx, 429) or {@link PermanentException} (other 4xx). Error messages name the operation,
 * never the URL, because Discord interaction URLs and webhook URLs contain credentials.
 */
public final class HttpCalls {

    private HttpCalls() {
    }

    public static <T> T execute(String operation, RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            return request.exchange((req, res) -> {
                HttpStatusCode status = res.getStatusCode();
                if (status.is2xxSuccessful()) {
                    if (type == Void.class || status.value() == 204) {
                        return null;
                    }
                    return res.bodyTo(type);
                }
                String snippet = Redactor.redact(readSnippet(res.getBody()));
                if (status.value() == 429) {
                    throw new RetryableException(operation + " was rate limited", retryAfter(res.getHeaders().getFirst("Retry-After")));
                }
                if (status.is5xxServerError() || status.value() == 408) {
                    throw new RetryableException(operation + " failed with HTTP " + status.value() + ": " + snippet);
                }
                throw new PermanentException(operation + " failed with HTTP " + status.value() + ": " + snippet);
            });
        } catch (ResourceAccessException e) {
            Throwable cause = e.getMostSpecificCause();
            throw new RetryableException(operation + " could not be reached (" + cause.getClass().getSimpleName() + ")");
        }
    }

    private static String readSnippet(InputStream body) {
        if (body == null) {
            return "";
        }
        try (body) {
            byte[] bytes = body.readNBytes(300);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static Duration retryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(header.trim());
            return Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
