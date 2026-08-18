package com.routeopt.routing;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orders the stops of a single vehicle's route.
 *
 * <p>Two deterministic phases: {@link NearestNeighborSolver} builds a priority-biased greedy tour,
 * then {@link TwoOptImprover} runs 2-opt local search against the penalized objective in
 * {@link RouteEvaluator}. No language model is involved past this point — given the same stops and
 * configuration, the output is always identical.
 */
@Service
public class RouteOptimizer {

    private static final Logger log = LoggerFactory.getLogger(RouteOptimizer.class);

    private final AppProperties properties;
    private final DistanceMatrixProvider matrixProvider;
    private final RouteEvaluator evaluator;

    public RouteOptimizer(AppProperties properties, DistanceMatrixProvider matrixProvider) {
        this.properties = properties;
        this.matrixProvider = matrixProvider;
        this.evaluator = new RouteEvaluator(
                properties.routing().latePenaltyPerMinute(),
                properties.routing().priorityPenaltyPerPosition());
    }

    public OptimizationResult optimize(
            Coordinate depot, String depotLabel, List<RouteStop> stops, LocalTime departureTime) {

        if (stops.isEmpty()) {
            throw new IllegalArgumentException("Cannot build a route with no stops");
        }

        List<String> warnings = new ArrayList<>();

        // Matrix index 0 is the depot; stop i lives at index i+1.
        List<Coordinate> points = new ArrayList<>(stops.size() + 1);
        points.add(depot);
        stops.forEach(stop -> points.add(stop.coordinate()));

        DistanceMatrix matrix = matrixProvider.compute(points);

        int[] initialSequence = NearestNeighborSolver.solve(stops, matrix);
        RouteEvaluation initial = evaluator.evaluate(initialSequence, stops, matrix, departureTime);

        int[] optimizedSequence = TwoOptImprover.improve(
                initialSequence,
                stops,
                matrix,
                departureTime,
                evaluator,
                properties.routing().maxIterations());
        RouteEvaluation optimized = evaluator.evaluate(optimizedSequence, stops, matrix, departureTime);

        // The local search only ever accepts strictly improving moves, so this should hold by
        // construction. Assert it anyway: silently returning a worse tour than the greedy one would
        // be the single most misleading failure this component could have.
        if (optimized.cost() > initial.cost() + 1e-6) {
            throw new IllegalStateException(
                    "2-opt returned a worse tour than the greedy solution: %f > %f"
                            .formatted(optimized.cost(), initial.cost()));
        }

        optimized.schedule().stream()
                .filter(StopSchedule::isLate)
                .forEach(entry -> warnings.add(
                        "Stop %d (%s) arrives %d minute(s) after its %s deadline"
                                .formatted(
                                        entry.sequence(),
                                        entry.stop().label(),
                                        entry.lateMinutes(),
                                        entry.stop().timeWindow().to())));

        log.info(
                "Optimized {} stops with {}: {} m -> {} m ({}% better)",
                stops.size(),
                matrix.provider(),
                Math.round(initial.totalDistanceMeters()),
                Math.round(optimized.totalDistanceMeters()),
                Math.round((initial.totalDistanceMeters() - optimized.totalDistanceMeters())
                        / Math.max(1, initial.totalDistanceMeters()) * 100));

        return new OptimizationResult(
                depot,
                depotLabel,
                optimized,
                initial.totalDistanceMeters(),
                initial.cost(),
                matrix.provider(),
                List.copyOf(warnings));
    }

    /** Exposed so tests and the health endpoint can see which matrix implementation is wired in. */
    public String matrixProviderName() {
        return matrixProvider.name();
    }

    /** Convenience for tests: the identity sequence 0..n-1. */
    static int[] identitySequence(int n) {
        return IntStream.range(0, n).toArray();
    }
}
