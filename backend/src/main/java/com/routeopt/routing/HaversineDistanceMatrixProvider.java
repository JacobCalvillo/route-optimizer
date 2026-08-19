package com.routeopt.routing;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Great-circle distances, scaled by a detour factor to approximate driving on a road network.
 *
 * <p>Straight-line distance systematically underestimates road distance, so the raw Haversine
 * result is multiplied by {@code app.routing.detour-factor} (1.3 by default, a common figure for
 * dense urban grids). Durations come from a flat average speed. This is an approximation by
 * design; switch {@code app.routing.matrix} to {@code osrm} when real road data matters.
 */
@Component
@ConditionalOnProperty(name = "app.routing.matrix", havingValue = "haversine")
public class HaversineDistanceMatrixProvider implements DistanceMatrixProvider {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final AppProperties properties;

    public HaversineDistanceMatrixProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public DistanceMatrix compute(List<Coordinate> points) {
        int n = points.size();
        double[][] distances = new double[n][n];
        double[][] durations = new double[n][n];

        double detourFactor = properties.routing().detourFactor();
        double metersPerSecond = properties.routing().averageSpeedKmh() * 1000.0 / 3600.0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double meters = haversineMeters(points.get(i), points.get(j)) * detourFactor;
                distances[i][j] = meters;
                distances[j][i] = meters;

                double seconds = meters / metersPerSecond;
                durations[i][j] = seconds;
                durations[j][i] = seconds;
            }
        }
        return new DistanceMatrix(distances, durations, name());
    }

    @Override
    public String name() {
        return "haversine";
    }

    /** Great-circle distance in metres between two WGS84 points. */
    public static double haversineMeters(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.lon() - a.lon());

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
