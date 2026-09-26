package dev.relaybot.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.relaybot.common.Redactor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * The Interactions Endpoint URL registered in the Discord Developer Portal.
 *
 * <p>Order matters: 1) verify the signature over the raw bytes, 2) answer PING without touching the
 * database, 3) hand everything else to {@link InteractionService}. If the database is down we still
 * answer inside the 3-second window, telling the user plainly that nothing was recorded.
 */
@RestController
public class InteractionController {

    private static final Logger log = LoggerFactory.getLogger(InteractionController.class);
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final SignatureVerifier verifier;
    private final InteractionService service;
    private final RejectionLog rejections;
    private final ObjectMapper json;

    public InteractionController(SignatureVerifier verifier, InteractionService service,
                                 RejectionLog rejections, ObjectMapper json) {
        this.verifier = verifier;
        this.service = service;
        this.rejections = rejections;
        this.json = json;
    }

    @PostMapping(path = "/api/discord/interactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> receive(
            @RequestHeader(name = "X-Signature-Ed25519", required = false) String signature,
            @RequestHeader(name = "X-Signature-Timestamp", required = false) String timestamp,
            // byte[] = the exact bytes Discord signed. Never bind this to a POJO/JsonNode before verifying.
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request) {

        if (body != null && body.length > MAX_BODY_BYTES) {
            rejections.record("Body too large", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "payload too large"));
        }

        SignatureVerifier.Result result = verifier.verify(signature, timestamp, body == null ? new byte[0] : body);
        if (result != SignatureVerifier.Result.VALID) {
            rejections.record(reason(result), request.getRemoteAddr());
            log.warn("rejected interaction request: {} from {}", result, request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid request signature"));
        }

        JsonNode payload;
        try {
            payload = json.readTree(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "malformed JSON"));
        }

        int type = payload.path("type").asInt();
        if (type == InteractionResponses.PING) {
            return ResponseEntity.ok(InteractionResponses.pong());
        }

        String interactionId = payload.path("id").asText();
        try (MDC.MDCCloseable a = MDC.putCloseable("interactionId", interactionId);
             MDC.MDCCloseable b = MDC.putCloseable("guildId", payload.path("guild_id").asText(null))) {
            return ResponseEntity.ok(service.handle(payload));
        } catch (DataAccessException e) {
            // The transaction rolled back, so there is no half-recorded state. Tell the user honestly.
            log.error("database unavailable while handling interaction: {}", e.getClass().getSimpleName());
            return ResponseEntity.ok(InteractionResponses.ephemeral(
                    "RelayBot couldn't save this right now, so nothing was recorded. Please try again in a minute."));
        } catch (RuntimeException e) {
            log.error("failed to handle interaction: {}", Redactor.redact(String.valueOf(e)));
            return ResponseEntity.ok(InteractionResponses.ephemeral(
                    "Something went wrong and nothing was recorded. Please try again."));
        }
    }

    private static String reason(SignatureVerifier.Result result) {
        return switch (result) {
            case MISSING_HEADERS -> "Missing signature headers";
            case BAD_SIGNATURE -> "Invalid signature";
            case STALE_TIMESTAMP -> "Expired timestamp (possible replay)";
            case VALID -> "Valid";
        };
    }
}
