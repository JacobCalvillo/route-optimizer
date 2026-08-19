package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the shape of the road the vehicle actually drives, for display.
 *
 * <p>Separate from {@link DistanceMatrixProvider} because they answer different questions. The
 * matrix answers "how far is every stop from every other stop", which the optimizer needs before it
 * knows the order. This answers "what does the chosen sequence look like on the ground", which only
 * makes sense once the order exists.
 *
 * <p>Returning {@link Optional#empty()} is a legitimate answer: it means "no road data available",
 * and the UI then draws straight segments and says so rather than pretending.
 */
public interface RouteGeometryProvider {

    /**
     * @param orderedPoints the full tour in visit order, depot first and depot again last
     * @return the road polyline as WGS84 points, or empty when no road data is available
     */
    Optional<List<Coordinate>> geometryFor(List<Coordinate> orderedPoints);

    /** Short identifier surfaced in the API response so the client knows what it is drawing. */
    String name();
}
