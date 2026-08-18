package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import java.util.List;

/**
 * Supplies the travel cost between every pair of points.
 *
 * <p>Two implementations exist: a Haversine approximation that needs no network, and an OSRM
 * adapter that returns real road distances. Which one is active is decided by the
 * {@code app.routing.matrix} property, so swapping them is a configuration change.
 */
public interface DistanceMatrixProvider {

    DistanceMatrix compute(List<Coordinate> points);

    /** Short identifier surfaced by {@code /api/health} so the active provider is visible. */
    String name();
}
