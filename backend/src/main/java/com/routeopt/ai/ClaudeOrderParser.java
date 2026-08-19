package com.routeopt.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.routeopt.config.AppProperties;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Extracts structured orders from free-form text using the Claude API's structured outputs. */
@Component
public class ClaudeOrderParser implements OrderParser {

    private static final Logger log = LoggerFactory.getLogger(ClaudeOrderParser.class);

    /*
     * Written in English, but the input it will see is Spanish (Mexico). Saying so explicitly is
     * what keeps the model from "helpfully" translating addresses before returning them.
     *
     * The closing paragraph is not boilerplate. The derived schema marks every field required and
     * non-nullable, so asking for null here would put the prompt and the grammar in direct
     * conflict — see ParsedOrder for what that conflict does to the output.
     */
    private static final String SYSTEM_PROMPT =
            """
            You extract delivery orders from dispatcher notes for a courier company in Mexico.

            The input is free-form text, normally written in Mexican Spanish, and may describe
            several deliveries in one go, separated by semicolons, line breaks, or just prose.
            Return one entry per delivery, in the order they appear. A note that lists six
            deliveries must produce six entries.

            Rules:
            - Copy the address verbatim from the input. Do not translate it, expand abbreviations,
              add a city or postal code that is not there, or invent an address.
            - priority is URGENT when the text conveys haste ("urgente", "lo antes posible", "es
              para ya", "prioridad alta"), LOW when it explicitly says there is no rush ("sin
              prisa", "cuando se pueda", "no corre prisa"), and NORMAL otherwise. Do not guess
              URGENT from a delivery window alone.
            - timeFrom and timeTo are HH:mm on a 24-hour clock. "antes de las 13:00" gives only
              timeTo, "despues de las 9" gives only timeFrom, "entre 10 y 12" gives both. Convert
              12-hour expressions ("5 de la tarde") to 24-hour form.
            - Put anything else useful (apartment or suite number, phone, gate code, "dejar con el
              portero") in notes, in the original language. Never put the address in notes.

            Every field is required and every value must be a string. When a value is absent from
            the text, return an empty string for it. Never write the word "null", and never carry a
            value from one delivery into another.

            Extract only what the text actually says. Never fill a field by assumption.
            """;

    private final AppProperties properties;
    private final OutputConfig.Effort effort;

    /** Built on first use so the app still starts (and /api/health still answers) without a key. */
    private volatile AnthropicClient client;

    public ClaudeOrderParser(AppProperties properties) {
        this.properties = properties;
        this.effort = resolveEffort(properties.ai().effort());
    }

    /**
     * Turns the configured effort level into the wire value the API accepts.
     *
     * <p>Two things make this worth doing once at startup rather than inline. The API only accepts
     * lowercase, and {@code Effort.of} wraps whatever string it is handed without normalizing it,
     * so a natural-looking {@code effort: LOW} in application.yml is rejected — but only when the
     * first extraction runs, long after the misconfiguration was introduced. Validating here turns
     * that into a boot failure with a message that names the accepted values.
     */
    private static OutputConfig.Effort resolveEffort(String configured) {
        String value = configured == null || configured.isBlank()
                ? "low"
                : configured.trim().toLowerCase(Locale.ROOT);
        OutputConfig.Effort resolved = OutputConfig.Effort.of(value);
        if (!resolved.isValid()) {
            throw new IllegalArgumentException(
                    "app.ai.effort must be one of low, medium, high, xhigh, max but was: " + configured);
        }
        return resolved;
    }

    @Override
    public boolean isAvailable() {
        String key = properties.ai().apiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public ParsedOrders parse(String freeText) {
        if (freeText == null || freeText.isBlank()) {
            return new ParsedOrders(List.of());
        }
        if (!isAvailable()) {
            throw new OrderParsingException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ANTHROPIC_API_KEY is not set, so free-form order parsing is unavailable.");
        }

        // Schema and effort have to be set on the same StructuredOutputConfig: the shorthand
        // .outputConfig(ParsedOrders.class) and .outputConfig(OutputConfig) occupy the same builder
        // slot, so using the shorthand would silently discard the effort level.
        StructuredOutputConfig<ParsedOrders> outputConfig = StructuredOutputConfig
                .<ParsedOrders>builder()
                .format(ParsedOrders.class)
                .effort(effort)
                .build();

        StructuredMessageCreateParams<ParsedOrders> params = MessageCreateParams.builder()
                .model(properties.ai().model())
                .maxTokens(properties.ai().maxTokens())
                .system(SYSTEM_PROMPT)
                .outputConfig(outputConfig)
                .addUserMessage(freeText)
                .build();

        try {
            ParsedOrders parsed = client().messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new OrderParsingException(
                                            HttpStatus.BAD_GATEWAY,
                                            "The model returned no text block to parse."));
            log.debug("Parsed {} order(s) from {} characters of input",
                    parsed.ordersOrEmpty().size(), freeText.length());
            return parsed;
        } catch (RateLimitException ex) {
            throw new OrderParsingException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limited by the Claude API; retry shortly.",
                    requestIdOf(ex),
                    ex);
        } catch (AnthropicServiceException ex) {
            throw new OrderParsingException(
                    HttpStatus.BAD_GATEWAY,
                    "The Claude API rejected the request: " + ex.getMessage(),
                    requestIdOf(ex),
                    ex);
        }
    }

    /** The API returns its trace id in a response header; surfacing it makes support tickets useful. */
    private static String requestIdOf(AnthropicServiceException ex) {
        List<String> values = ex.headers().values("request-id");
        return values.isEmpty() ? null : values.getFirst();
    }

    private AnthropicClient client() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    // The key is passed explicitly rather than via fromEnv() so it can also come
                    // from backend/.env, which the JVM's own environment never sees.
                    local = AnthropicOkHttpClient.builder()
                            .apiKey(properties.ai().apiKey())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }
}
