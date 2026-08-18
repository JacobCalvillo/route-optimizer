package com.routeopt.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.routeopt.config.AppProperties;
import java.util.List;
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
     */
    private static final String SYSTEM_PROMPT =
            """
            You extract delivery orders from dispatcher notes for a courier company in Mexico.

            The input is free-form text, normally written in Mexican Spanish, and may describe \
            several deliveries in one go, separated by semicolons, line breaks, or just prose. \
            Return exactly one entry per delivery.

            Rules:
            - Copy the address verbatim from the input. Do not translate it, expand abbreviations, \
              add a city or postal code that is not there, or invent an address. If a delivery has \
              no recognizable address, set address to null and keep whatever else you found.
            - priority is URGENT when the text conveys haste ("urgente", "lo antes posible", \
              "es para ya", "prioridad alta"), LOW when it explicitly says there is no rush \
              ("sin prisa", "cuando se pueda", "no corre prisa"), and NORMAL otherwise. Do not \
              guess URGENT from a delivery window alone.
            - timeFrom and timeTo are HH:mm on a 24-hour clock. "antes de las 13:00" gives only \
              timeTo, "despues de las 9" gives only timeFrom, "entre 10 y 12" gives both. Convert \
              12-hour expressions ("5 de la tarde") to 24-hour form. Leave them null when absent.
            - Put anything else useful (apartment or suite number, phone, gate code, "dejar con el \
              portero") in notes, in the original language. Never put the address in notes.

            Extract only what the text actually says. Never fill a field by assumption.
            """;

    private final AppProperties properties;

    /** Built on first use so the app still starts (and /api/health still answers) without a key. */
    private volatile AnthropicClient client;

    public ClaudeOrderParser(AppProperties properties) {
        this.properties = properties;
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

        StructuredMessageCreateParams<ParsedOrders> params = MessageCreateParams.builder()
                .model(properties.ai().model())
                .maxTokens(properties.ai().maxTokens())
                .system(SYSTEM_PROMPT)
                .outputConfig(ParsedOrders.class)
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
