package com.routeopt.routing;

import java.util.List;

/**
 * Greedy construction heuristic: start at the depot and repeatedly jump to the "closest" unvisited
 * stop.
 *
 * <p>The twist is that closeness is divided by the stop's priority weight, so an URGENT stop is
 * treated as if it were three times nearer than it really is. That biases urgency into the initial
 * tour instead of leaving it entirely to the local search, which starts from a much better place
 * as a result.
 *
 * <p>Runs in O(n²) and produces a tour that is typically 15–25% worse than optimal — which is
 * exactly why {@link TwoOptImprover} runs afterwards.
 */
public final class NearestNeighborSolver {

    private NearestNeighborSolver() {}

    /** Returns a permutation of {@code 0..stops.size()-1} (indices into {@code stops}). */
    public static int[] solve(List<RouteStop> stops, DistanceMatrix matrix) {
        int n = stops.size();
        int[] sequence = new int[n];
        boolean[] visited = new boolean[n];

        int currentMatrixIndex = 0; // depot
        for (int position = 0; position < n; position++) {
            int best = -1;
            double bestScore = Double.MAX_VALUE;

            for (int candidate = 0; candidate < n; candidate++) {
                if (visited[candidate]) {
                    continue;
                }
                double distance = matrix.distance(currentMatrixIndex, candidate + 1);
                double score = distance / stops.get(candidate).priority().weight();
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            visited[best] = true;
            sequence[position] = best;
            currentMatrixIndex = best + 1;
        }
        return sequence;
    }
}
