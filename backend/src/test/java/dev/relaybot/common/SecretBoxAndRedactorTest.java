package dev.relaybot.common;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBoxAndRedactorTest {

    private final SecretBox box = new SecretBox(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsAndDecrypts() {
        String url = "https://hooks.slack.com/services/T0/B0/secret";
        String enc = box.encrypt(url);
        assertThat(enc).doesNotContain("secret");
        assertThat(box.decrypt(enc)).isEqualTo(url);
        assertThat(box.encrypt(url)).isNotEqualTo(enc);   // random IV each time
    }

    @Test
    void detectsTampering() {
        String enc = box.encrypt("value");
        byte[] raw = Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> box.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void redactsCredentialsFromErrorMessages() {
        String leaked = "I/O error on PATCH request for \"https://discord.com/api/v10/webhooks/1/SECRETTOKEN/messages/@original\""
                + " and https://hooks.slack.com/services/T/B/SLACKSECRET, header Bot abcdefghijklmnopqrstuvwxyz.123456";
        String out = Redactor.redact(leaked);
        assertThat(out).doesNotContain("SECRETTOKEN", "SLACKSECRET", "abcdefghijklmnopqrstuvwxyz");
    }
}
