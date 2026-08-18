package com.routeopt.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real road distances and durations from an OSRM server's {@code /table} service.
 *
 * <p>Activated by setting {@code app.routing.matrix=osrm}. If the server is unreachable, slow, or
 * returns an incomplete table, this falls back to the Haversine approximation rather than failing
 * the request: a demo that silently degrades beats a demo that 502s.
 */
@Component
@ConditionalOnProperty(name = "app.routing.matrix", havingValue = "osrm")
public class OsrmDistanceMatrixProvider implements DistanceMatrixProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmDistanceMatrixProvider.class);

    private final AppProperties properties;
    private final RestClient restClient;
    private final HaversineDistanceMatrixProvider fallback;

    public OsrmDistanceMatrixProvider(AppProperties properties) {
        this.properties = properties;
        this.fallback = new HaversineDistanceMatrixProvider(properties);

        Duration timeout = Duration.ofSeconds(properties.routing().osrm().timeoutSeconds());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.routing().osrm().baseUrl())
                .requestFactory((ClientHttpRequestFactory) factory)
                .build();
    }

    @Override
    public DistanceMatrix compute(List<Coordinate> points) {
        try {
            // OSRM takes lon,lat pairs separated by semicolons - note the axis order.
            String path = points.stream()
                    .map(point -> String.format(Locale.ROOT, "%f,%f", point.lon(), point.lat()))
                    .collect(Collectors.joining(";"));

            TableResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/table/v1/driving/{coordinates}")
                            .queryParam("annotations", "duration,distance")
                            .build(path))
                    .retrieve()
                    .body(TableResponse.class);

            if (response == null
                    || !"Ok".equalsIgnoreCase(response.code())
                    || response.distances() == null
                    || response.durations() == null
                    || response.distances().length != points.size()) {
                log.warn("OSRM returned an unusable table; falling back to Haversine");
                return fallbackMatrix(points);
            }

            return new DistanceMatrix(response.distances(), response.durations(), name());
        } catch (RuntimeException ex) {
            log.warn("OSRM request failed ({}); falling back to Haversine", ex.getMessage());
            return fallbackMatrix(points);
        }
    }

    @Override
    public String name() {
        return "osrm";
    }

    /** Marks the provider name so the response makes the degradation visible to the caller. */
    private DistanceMatrix fallbackMatrix(List<Coordinate> points) {
        DistanceMatrix haversine = fallback.compute(points);
        return new DistanceMatrix(
                haversine.distanceMeters(), haversine.durationSeconds(), "haversine (osrm fallback)");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TableResponse(String code, double[][] distances, double[][] durations) {}
}
