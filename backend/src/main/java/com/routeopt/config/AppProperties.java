package com.routeopt.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Every tunable knob of the application, bound from the {@code app.*} section of application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Ai ai, Geocoding geocoding, Routing routing) {

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {}

    public record Ai(
            @DefaultValue("claude-opus-5") String model,
            /*
             * On claude-opus-5 thinking is on by default and max-tokens caps thinking plus the
             * answer, so a value tuned for the JSON alone would truncate the response.
             */
            @DefaultValue("8000") long maxTokens,
            @DefaultValue("LOW") String effort) {}

    public record Geocoding(
            @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
            @DefaultValue("route-optimizer/0.1") String userAgent,
            @DefaultValue("mx") String countryCodes,
            /* Nominatim's usage policy allows at most one request per second. */
            @DefaultValue("1100") long minIntervalMillis) {}

    public record Routing(
            @DefaultValue("haversine") String matrix,
            @DefaultValue("1.3") double detourFactor,
            @DefaultValue("30") double averageSpeedKmh,
            @DefaultValue("5") int defaultServiceMinutes,
            @DefaultValue("500") double latePenaltyPerMinute,
            @DefaultValue("200") double priorityPenaltyPerPosition,
            @DefaultValue("1000") int maxIterations,
            Osrm osrm) {

        public record Osrm(
                @DefaultValue("https://router.project-osrm.org") String baseUrl,
                @DefaultValue("10") int timeoutSeconds) {}
    }
}
