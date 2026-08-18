package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import java.util.List;

/**
 * The optimizer's output, including the "before" figures.
 *
 * <p>{@code initialDistanceMeters} is the greedy tour's distance and {@code totalDistanceMeters} is
 * the distance after 2-opt. Keeping both is what lets the UI show that the local search actually
 * did something rather than just asserting it.
 */
public record OptimizationResult(
        Coordinate depot,
        String depotLabel,
        RouteEvaluation evaluation,
        double initialDistanceMeters,
        double initialCost,
        String matrixProvider,
        List<String> warnings) {

    public double totalDistanceMeters() {
        return evaluation.totalDistanceMeters();
    }

    /** Percentage of the greedy tour's distance saved by the local search; 0 when it found nothing. */
    public double improvementPercent() {
        if (initialDistanceMeters <= 0) {
            return 0;
        }
        double improvement =
                (initialDistanceMeters - evaluation.totalDistanceMeters()) / initialDistanceMeters * 100.0;
        return Math.max(0, improvement);
    }
}
