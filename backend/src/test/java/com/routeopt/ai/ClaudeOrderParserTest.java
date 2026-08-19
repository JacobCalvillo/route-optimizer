package com.routeopt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeopt.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Covers everything the parser decides before it reaches the network, which is where its two real
 * failure modes live: a misconfigured effort level, and a missing API key.
 */
class ClaudeOrderParserTest {

    private static AppProperties propertiesWith(String apiKey, String effort) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Ai(apiKey, "claude-opus-5", 8000, effort),
                new AppProperties.Geocoding("http://localhost", "test", "mx", 0),
                new AppProperties.Routing(
                        "haversine", 1.3, 30, 5, 500, 200, 1000,
                        new AppProperties.Routing.Osrm("http://localhost", 10)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"low", "LOW", "Low", " medium ", "xhigh", "MAX"})
    void acceptsEveryValidEffortRegardlessOfCasing(String configured) {
        // The API only accepts lowercase and the SDK does not normalize, so a natural-looking
        // `effort: LOW` in application.yml would otherwise fail on the first extraction.
        assertThatCode(() -> new ClaudeOrderParser(propertiesWith("sk-test", configured)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lo", "urgent", "HIGHEST", "1"})
    void rejectsAnInvalidEffortAtStartupRatherThanOnFirstUse(String configured) {
        assertThatThrownBy(() -> new ClaudeOrderParser(propertiesWith("sk-test", configured)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.ai.effort")
                .hasMessageContaining("low, medium, high, xhigh, max")
                .hasMessageContaining(configured);
    }

    @Test
    void fallsBackToLowWhenEffortIsBlank() {
        assertThatCode(() -> new ClaudeOrderParser(propertiesWith("sk-test", "  ")))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsUnavailableWithoutAnApiKey() {
        assertThat(new ClaudeOrderParser(propertiesWith("", "low")).isAvailable()).isFalse();
        assertThat(new ClaudeOrderParser(propertiesWith("   ", "low")).isAvailable()).isFalse();
        assertThat(new ClaudeOrderParser(propertiesWith("sk-test", "low")).isAvailable()).isTrue();
    }

    @Test
    void failsLoudlyRatherThanSilentlyWhenNoKeyIsConfigured() {
        assertThatThrownBy(() -> new ClaudeOrderParser(propertiesWith("", "low")).parse("Reforma 222"))
                .isInstanceOf(OrderParsingException.class)
                .satisfies(ex -> assertThat(((OrderParsingException) ex).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void doesNotCallTheApiForEmptyInput() {
        // No key configured, so reaching the network would throw. Returning empty proves it did not.
        ClaudeOrderParser parser = new ClaudeOrderParser(propertiesWith("", "low"));
        assertThat(parser.parse("   ").ordersOrEmpty()).isEmpty();
        assertThat(parser.parse(null).ordersOrEmpty()).isEmpty();
    }
}
