package com.routeopt.domain;

/** A WGS84 point. */
public record Coordinate(double lat, double lon) {

    public Coordinate {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude out of range: " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Longitude out of range: " + lon);
        }
    }
}
