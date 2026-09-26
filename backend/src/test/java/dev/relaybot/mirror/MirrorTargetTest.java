package dev.relaybot.mirror;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MirrorTargetTest {

    @Test
    void recognisesSlackAndDiscord() {
        assertThat(MirrorTarget.parse("https://hooks.slack.com/services/T0/B0/xyz").kind()).isEqualTo(MirrorTarget.Kind.SLACK);
        assertThat(MirrorTarget.parse("https://discord.com/api/webhooks/1/abc").kind()).isEqualTo(MirrorTarget.Kind.DISCORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://hooks.slack.com/services/x",          // not https
            "https://evil.com/api/webhooks/1/a",           // wrong host
            "https://169.254.169.254/latest/meta-data",    // SSRF attempt
            "https://discord.com.evil.io/api/webhooks/1",  // lookalike host
            "https://user@hooks.slack.com/services/x",     // userinfo
            "https://hooks.slack.com:8443/services/x",     // odd port
            "https://discord.com/channels/1/2"             // not a webhook
    })
    void rejectsAnythingElse(String url) {
        assertThatThrownBy(() -> MirrorTarget.parse(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverPrintsTheUrl() {
        assertThat(MirrorTarget.parse("https://discord.com/api/webhooks/1/secret").toString()).doesNotContain("secret");
    }

    @Test
    void escapesSlackControlCharacters() {
        assertThat(MirrorClient.escapeSlack("<!channel> & <@U1>")).isEqualTo("&lt;!channel&gt; &amp; &lt;@U1&gt;");
    }
}
