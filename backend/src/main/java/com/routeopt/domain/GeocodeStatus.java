package com.routeopt.domain;

/** Outcome of resolving an order's free-form address to coordinates. */
public enum GeocodeStatus {
    /** Not attempted yet. */
    PENDING,
    /** Resolved to a single set of coordinates. */
    OK,
    /** The AI could not find an address in the text, so there was nothing to resolve. */
    NO_ADDRESS,
    /** The geocoder returned no usable match. */
    FAILED
}
