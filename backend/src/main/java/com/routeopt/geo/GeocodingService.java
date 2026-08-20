package com.routeopt.geo;

import com.routeopt.domain.PostalAddress;

/** Resolves an address to coordinates. */
public interface GeocodingService {

    /** Free-form lookup, for an address that only exists as one string. */
    GeocodeResult geocode(String address);

    /**
     * Structured lookup, which is markedly more reliable when the parts are known.
     *
     * <p>Verified against the live service: a Nuevo Leon address that free-form search could not
     * find at all resolves on the first structured attempt, and an ambiguous "Paseo de la Reforma
     * 222" that free-form placed in Quintana Roo lands in Mexico City once the city is its own
     * parameter.
     */
    GeocodeResult geocode(PostalAddress address);
}
