package com.routeopt.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Road geometry from an OSRM server's {@code /route} service, split per leg.
 *
 * <p>This is what makes the drawn line follow streets instead of cutting across blocks. Without it
 * the map contradicts its own summary: the distance would be a road distance while the polyline
 * showed the straight-line path.
 *
 * <p>The request asks for {@code steps} and skips the overview polyline entirely. OSRM only fills
 * in per-leg geometry when steps are requested, and the overview would just be the same line again
 * — the client can concatenate the legs when it wants the whole thing.
 *
 * <p>Failure is not an error. If OSRM is unreachable this returns empty and the client falls back
 * to straight segments, which is the same degradation the distance matrix already performs.
 */
@Component
@ConditionalOnProperty(name = "app.routing.matrix", havingValue = "osrm", matchIfMissing = true)
public class OsrmRouteGeometryProvider implements RouteGeometryProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteGeometryProvider.class);

    private final AppProperties properties;
    private final RestClient restClient;

    public OsrmRouteGeometryProvider(AppProperties properties) {
        this.properties = properties;
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
    public Optional<List<List<Coordinate>>> legsFor(List<Coordinate> orderedPoints) {
        if (orderedPoints.size() < 2) {
            return Optional.empty();
        }
        try {
            // OSRM takes lon,lat pairs separated by semicolons - note the axis order.
            String path = orderedPoints.stream()
                    .map(point -> String.format(Locale.ROOT, "%f,%f", point.lon(), point.lat()))
                    .collect(Collectors.joining(";"));

            RouteResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coordinates}")
                            .queryParam("steps", "true")
                            .queryParam("overview", properties.routing().osrm().overview())
                            .queryParam("geometries", "geojson")
                            .build(path))
                    .retrieve()
                    .body(RouteResponse.class);

            if (response == null
                    || !"Ok".equalsIgnoreCase(response.code())
                    || response.routes() == null
                    || response.routes().isEmpty()
                    || response.routes().getFirst().legs() == null) {
                log.warn("OSRM returned no route geometry; the client will draw straight segments");
                return Optional.empty();
            }

            List<List<Coordinate>> legs = new ArrayList<>();
            for (Leg leg : response.routes().getFirst().legs()) {
                List<Coordinate> line = new ArrayList<>();
                if (leg.steps() != null) {
                    for (Step step : leg.steps()) {
                        if (step.geometry() == null || step.geometry().coordinates() == null) {
                            continue;
                        }
                        for (List<Double> pair : step.geometry().coordinates()) {
                            if (pair.size() < 2) {
                                continue;
                            }
                            // GeoJSON is [longitude, latitude]; mapping libraries expect the reverse.
                            Coordinate point = new Coordinate(pair.get(1), pair.get(0));
                            // Consecutive steps repeat the shared vertex; drop the duplicate.
                            if (line.isEmpty() || !line.getLast().equals(point)) {
                                line.add(point);
                            }
                        }
                    }
                }
                legs.add(List.copyOf(line));
            }

            if (legs.isEmpty() || legs.stream().allMatch(List::isEmpty)) {
                return Optional.empty();
            }
            log.debug("OSRM returned {} leg(s), {} points total",
                    legs.size(), legs.stream().mapToInt(List::size).sum());
            return Optional.of(List.copyOf(legs));
        } catch (RuntimeException ex) {
            log.warn("OSRM geometry request failed ({}); the client will draw straight segments",
                    ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "osrm";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteResponse(String code, List<Route> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(List<Leg> legs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Leg(List<Step> steps) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(Geometry geometry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {}
}
