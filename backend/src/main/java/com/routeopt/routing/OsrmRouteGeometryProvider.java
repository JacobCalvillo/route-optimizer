package com.routeopt.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.time.Duration;
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
 * Road geometry from an OSRM server's {@code /route} service.
 *
 * <p>This is what makes the drawn line follow streets instead of cutting across blocks. Without it
 * the map contradicts its own summary: the distance would be a road distance while the polyline
 * showed the straight-line path.
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
    public Optional<List<Coordinate>> geometryFor(List<Coordinate> orderedPoints) {
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
                            // Point count scales with route length: "full" is a few thousand
                            // points for a city day and tens of thousands for an intercity one.
                            // "simplified" caps the payload at the cost of visibly cutting corners
                            // when zoomed in.
                            .queryParam("overview", properties.routing().osrm().overview())
                            .queryParam("geometries", "geojson")
                            .build(path))
                    .retrieve()
                    .body(RouteResponse.class);

            if (response == null
                    || !"Ok".equalsIgnoreCase(response.code())
                    || response.routes() == null
                    || response.routes().isEmpty()) {
                log.warn("OSRM returned no route geometry; the client will draw straight segments");
                return Optional.empty();
            }

            // GeoJSON is [longitude, latitude]; every mapping library expects the opposite.
            List<Coordinate> line = response.routes().getFirst().geometry().coordinates().stream()
                    .filter(pair -> pair.size() >= 2)
                    .map(pair -> new Coordinate(pair.get(1), pair.get(0)))
                    .toList();

            log.debug("OSRM returned road geometry with {} points", line.size());
            return line.isEmpty() ? Optional.empty() : Optional.of(line);
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
    record RouteResponse(String code, List<Route> routes) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Route(Geometry geometry) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Geometry(List<List<Double>> coordinates) {}
    }
}
