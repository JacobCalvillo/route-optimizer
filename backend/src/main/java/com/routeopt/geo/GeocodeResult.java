package com.routeopt.geo;

import com.routeopt.domain.Coordinate;

/**
 * Outcome of a geocoding lookup. {@code coordinate} and {@code displayName} are null when the
 * address could not be resolved.
 *
 * <p>{@code exact} is false when the match came from a simplified query rather than the address as
 * written — the street or the neighbourhood was found, but not the house number. That is usually
 * good enough to sequence a stop and never good enough to present as an exact match, which is why
 * it travels with the result instead of being discarded.
 */
public record GeocodeResult(
        boolean found,
        Coordinate coordinate,
        String displayName,
        boolean fromCache,
        boolean exact,
        String matchedQuery) {

    public static GeocodeResult notFound(boolean fromCache) {
        return new GeocodeResult(false, null, null, fromCache, false, null);
    }

    public static GeocodeResult found(Coordinate coordinate, String displayName, boolean fromCache) {
        return new GeocodeResult(true, coordinate, displayName, fromCache, true, null);
    }

    /** A match found only after simplifying the address; {@code matchedQuery} is what worked. */
    public static GeocodeResult approximate(
            Coordinate coordinate, String displayName, boolean fromCache, String matchedQuery) {
        return new GeocodeResult(true, coordinate, displayName, fromCache, false, matchedQuery);
    }
}
