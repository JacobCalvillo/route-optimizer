package com.routeopt.routing;

/**
 * Pairwise travel cost between a list of points, in the order those points were supplied.
 *
 * <p>Index 0 is by convention the depot; indices 1..n are the delivery stops.
 */
public record DistanceMatrix(double[][] distanceMeters, double[][] durationSeconds, String provider) {

    public DistanceMatrix {
        if (distanceMeters.length != durationSeconds.length) {
            throw new IllegalArgumentException("Distance and duration matrices must have the same size");
        }
    }

    public int size() {
        return distanceMeters.length;
    }

    public double distance(int from, int to) {
        return distanceMeters[from][to];
    }

    public double duration(int from, int to) {
        return durationSeconds[from][to];
    }
}
