package dev.relaybot.discord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;

import static dev.relaybot.discord.SignatureVerifier.Result.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final byte[] BODY = "{\"type\":1, \"id\":\"123\"}".getBytes(StandardCharsets.UTF_8);

    private KeyPair discordKeys;
    private SignatureVerifier verifier;
    private String ts;

    @BeforeEach
    void setUp() throws Exception {
        discordKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] x509 = discordKeys.getPublic().getEncoded();
        String rawPublicKeyHex = HexFormat.of().formatHex(Arrays.copyOfRange(x509, x509.length - 32, x509.length));
        verifier = new SignatureVerifier(rawPublicKeyHex, 300, Clock.fixed(NOW, ZoneOffset.UTC));
        ts = String.valueOf(NOW.getEpochSecond());
    }

    @Test
    void acceptsCorrectlySignedRequest() throws Exception {
        assertThat(verifier.verify(sign(discordKeys, ts, BODY), ts, BODY)).isEqualTo(VALID);
    }

    @Test
    void rejectsReserialisedBody() throws Exception {
        // Same JSON, different whitespace: exactly what happens if the body is parsed before verifying.
        byte[] reserialised = "{\"type\":1,\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(sign(discordKeys, ts, BODY), ts, reserialised)).isEqualTo(BAD_SIGNATURE);
    }

    @Test
    void rejectsSignatureFromAnotherKey() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThat(verifier.verify(sign(attacker, ts, BODY), ts, BODY)).isEqualTo(BAD_SIGNATURE);
    }

    @Test
    void rejectsSwappedTimestamp() throws Exception {
        String sig = sign(discordKeys, ts, BODY);
        assertThat(verifier.verify(sig, String.valueOf(NOW.getEpochSecond() + 1), BODY)).isEqualTo(BAD_SIGNATURE);
    }

    @Test
    void rejectsOldReplay() throws Exception {
        String old = String.valueOf(NOW.getEpochSecond() - 600);
        assertThat(verifier.verify(sign(discordKeys, old, BODY), old, BODY)).isEqualTo(STALE_TIMESTAMP);
    }

    @Test
    void rejectsMissingOrGarbageHeaders() {
        assertThat(verifier.verify(null, ts, BODY)).isEqualTo(MISSING_HEADERS);
        assertThat(verifier.verify("abcd", "", BODY)).isEqualTo(MISSING_HEADERS);
        assertThat(verifier.verify("not-hex", ts, BODY)).isEqualTo(BAD_SIGNATURE);
        assertThat(verifier.verify("abcd", ts, BODY)).isEqualTo(BAD_SIGNATURE);
        assertThat(verifier.verify("00".repeat(64), "yesterday", BODY)).isEqualTo(BAD_SIGNATURE);
    }

    @Test
    void refusesToStartWithInvalidKey() {
        assertThatThrownBy(() -> new SignatureVerifier("1234", 300, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String sign(KeyPair keys, String timestamp, byte[] body) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(keys.getPrivate());
        s.update(timestamp.getBytes(StandardCharsets.UTF_8));
        s.update(body);
        return HexFormat.of().formatHex(s.sign());
    }
}
