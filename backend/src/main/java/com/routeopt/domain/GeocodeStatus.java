package com.routeopt.domain;

/** Outcome of resolving an order's free-form address to coordinates. */
public enum GeocodeStatus {
    /** Not attempted yet. */
    PENDING,
    /** Resolved to a single set of coordinates from the address as written. */
    OK,
    /**
     * Resolved, but only after simplifying the address: the street or the area was found rather
     * than the exact number. Good enough to sequence a stop, not good enough to present as exact.
     */
    APPROXIMATE,
    /** The AI could not find an address in the text, so there was nothing to resolve. */
    NO_ADDRESS,
    /** The geocoder returned no usable match. */
    FAILED
}
