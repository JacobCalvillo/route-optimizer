package com.routeopt.geo;

import com.routeopt.domain.Coordinate;

/**
 * Outcome of a geocoding lookup. {@code coordinate} and {@code displayName} are null when the
 * address could not be resolved.
 */
public record GeocodeResult(
        boolean found, Coordinate coordinate, String displayName, boolean fromCache) {

    public static GeocodeResult notFound(boolean fromCache) {
        return new GeocodeResult(false, null, null, fromCache);
    }

    public static GeocodeResult found(Coordinate coordinate, String displayName, boolean fromCache) {
        return new GeocodeResult(true, coordinate, displayName, fromCache);
    }
}
