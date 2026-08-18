package com.routeopt.geo;

/** Resolves a free-form address to coordinates. */
public interface GeocodingService {

    GeocodeResult geocode(String address);
}
