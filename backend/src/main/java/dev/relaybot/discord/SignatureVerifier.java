package dev.relaybot.discord;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Verifies Discord's Ed25519 request signature.
 *
 * <p>Discord signs {@code timestamp + rawBody} with the application's private key. We verify
 * against the <b>raw bytes</b> exactly as received: parsing and re-serialising the JSON first
 * changes whitespace/key order and breaks the signature.
 *
 * <p>The timestamp is part of the signed message, so checking it is fresh (default 5 minutes)
 * rejects replays of old, validly-signed requests. Replays inside the window are caught by
 * interaction-id dedup in the database.
 *
 * <p>Uses the JDK's built-in Ed25519 (Java 15+), so there is no crypto dependency.
 */
public class SignatureVerifier {

    public enum Result { VALID, MISSING_HEADERS, BAD_SIGNATURE, STALE_TIMESTAMP }

    /** DER prefix that wraps a raw 32-byte Ed25519 key into an X.509 SubjectPublicKeyInfo. */
    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private final PublicKey publicKey;
    private final long maxSkewSeconds;
    private final Clock clock;

    public SignatureVerifier(String publicKeyHex, long maxSkewSeconds, Clock clock) {
        this.publicKey = decodePublicKey(publicKeyHex);
        this.maxSkewSeconds = maxSkewSeconds;
        this.clock = clock;
    }

    public Result verify(String signatureHex, String timestamp, byte[] rawBody) {
        if (isBlank(signatureHex) || isBlank(timestamp) || rawBody == null) {
            return Result.MISSING_HEADERS;
        }
        final long timestampSeconds;
        final byte[] signature;
        try {
            timestampSeconds = Long.parseLong(timestamp.trim());
            signature = HexFormat.of().parseHex(signatureHex.trim());
        } catch (IllegalArgumentException e) {   // covers NumberFormatException and bad hex
            return Result.BAD_SIGNATURE;
        }
        if (signature.length != 64 || !signatureMatches(signature, timestamp, rawBody)) {
            return Result.BAD_SIGNATURE;
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestampSeconds) > maxSkewSeconds) {
            return Result.STALE_TIMESTAMP;
        }
        return Result.VALID;
    }

    private boolean signatureMatches(byte[] signature, String timestamp, byte[] rawBody) {
        try {
            // java.security.Signature is not thread-safe, so create one per request.
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));
            verifier.update(rawBody);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static PublicKey decodePublicKey(String hex) {
        if (isBlank(hex)) {
            throw new IllegalStateException("DISCORD_PUBLIC_KEY is not set");
        }
        try {
            byte[] raw = HexFormat.of().parseHex(hex.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("DISCORD_PUBLIC_KEY must be 32 bytes (64 hex characters)");
            }
            byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
            System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("DISCORD_PUBLIC_KEY is not a valid Ed25519 public key", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
