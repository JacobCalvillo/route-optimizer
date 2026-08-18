package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TwoOptImproverTest {

    private static final LocalTime DEPARTURE = LocalTime.of(8, 0);

    private final AppProperties properties = TestFixtures.properties(500, 200);

    /**
     * Four stops on the corners of a square with the depot at one corner. Visiting them in the
     * order given crosses the square's diagonals twice; the optimal tour walks the perimeter.
     */
    private List<RouteStop> squareStops() {
        return List.of(
                TestFixtures.stop("east", 19.4326, -99.0332),
                TestFixtures.stop("north-east", 19.5326, -99.0332),
                TestFixtures.stop("north", 19.5326, -99.1332));
    }

    @Test
    void untanglesADeliberatelyCrossedTour() {
        List<RouteStop> stops = squareStops();
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);
        RouteEvaluator evaluator = new RouteEvaluator(0, 0);

        // depot -> north-east -> east -> north -> depot crosses itself.
        int[] crossed = {1, 0, 2};
        double crossedCost = evaluator.evaluate(crossed, stops, matrix, DEPARTURE).cost();

        int[] improved = TwoOptImprover.improve(crossed, stops, matrix, DEPARTURE, evaluator, 1000);
        double improvedCost = evaluator.evaluate(improved, stops, matrix, DEPARTURE).cost();

        assertThat(improvedCost).isLessThan(crossedCost);
    }

    @Test
    void neverReturnsAWorseTourThanItWasGiven() {
        // The invariant that matters: local search only ever accepts strictly improving moves.
        Random random = new Random(20260818L);
        RouteEvaluator evaluator = new RouteEvaluator(500, 200);

        for (int trial = 0; trial < 25; trial++) {
            List<RouteStop> stops = randomStops(random, 8);
            DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

            int[] initial = NearestNeighborSolver.solve(stops, matrix);
            double initialCost = evaluator.evaluate(initial, stops, matrix, DEPARTURE).cost();

            int[] improved = TwoOptImprover.improve(initial, stops, matrix, DEPARTURE, evaluator, 1000);
            double improvedCost = evaluator.evaluate(improved, stops, matrix, DEPARTURE).cost();

            assertThat(improvedCost).isLessThanOrEqualTo(initialCost + 1e-9);
        }
    }

    @Test
    void preservesEveryStopExactlyOnce() {
        Random random = new Random(7L);
        List<RouteStop> stops = randomStops(random, 10);
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);
        RouteEvaluator evaluator = new RouteEvaluator(500, 200);

        int[] improved = TwoOptImprover.improve(
                NearestNeighborSolver.solve(stops, matrix), stops, matrix, DEPARTURE, evaluator, 1000);

        assertThat(improved).hasSize(stops.size());
        assertThat(java.util.Arrays.stream(improved).boxed().distinct().count()).isEqualTo(stops.size());
        assertThat(java.util.Arrays.stream(improved).max().orElseThrow()).isEqualTo(stops.size() - 1);
    }

    private static List<RouteStop> randomStops(Random random, int count) {
        List<RouteStop> stops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double lat = 19.30 + random.nextDouble() * 0.30;
            double lon = -99.30 + random.nextDouble() * 0.30;
            stops.add(new RouteStop(
                    (long) i,
                    "stop-" + i,
                    new Coordinate(lat, lon),
                    com.routeopt.domain.Priority.values()[random.nextInt(3)],
                    null,
                    5));
        }
        return stops;
    }
}
