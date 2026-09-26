package dev.relaybot.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final List<KeywordRule> rules = List.of(
            new KeywordRule("outage", Priority.HIGH),
            new KeywordRule("slow", Priority.MEDIUM),
            new KeywordRule("500", Priority.HIGH));

    @Test
    void keywordRuleWinsAndIsCaseInsensitive() {
        RuleEngine.Decision d = RuleEngine.decide("Total OUTAGE in prod", rules, Priority.LOW, Priority.LOW);
        assertThat(d.priority()).isEqualTo(Priority.HIGH);
        assertThat(d.source()).isEqualTo("RULE");
        assertThat(d.matchedKeyword()).isEqualTo("outage");
    }

    @Test
    void highestMatchingRuleWins() {
        assertThat(RuleEngine.decide("slow, then an outage", rules, null, Priority.LOW).matchedKeyword()).isEqualTo("outage");
    }

    @Test
    void matchesWholeWordsOnly() {
        assertThat(RuleEngine.decide("page loads slowly", rules, null, Priority.LOW).source()).isEqualTo("DEFAULT");
        assertThat(RuleEngine.decide("port 5000 is closed", rules, null, Priority.LOW).source()).isEqualTo("DEFAULT");
        assertThat(RuleEngine.decide("got a 500 error", rules, null, Priority.LOW).priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void fallsBackToAiThenDefault() {
        assertThat(RuleEngine.decide("hello", rules, Priority.MEDIUM, Priority.LOW).source()).isEqualTo("AI");
        RuleEngine.Decision d = RuleEngine.decide("hello", rules, null, Priority.LOW);
        assertThat(d.source()).isEqualTo("DEFAULT");
        assertThat(d.priority()).isEqualTo(Priority.LOW);
    }
}
