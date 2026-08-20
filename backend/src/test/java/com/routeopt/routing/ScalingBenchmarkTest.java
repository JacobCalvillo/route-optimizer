package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import com.routeopt.domain.Priority;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Measures how the optimizer scales, because "it is O(n³) per pass" is a claim and a millisecond
 * figure is an answer.
 *
 * <p>The assertion is deliberately loose — it exists so the test fails if something turns
 * accidentally quadratic in the number of *evaluations*, not to pin a wall-clock number that would
 * be flaky on a slower machine. The printed table is the point.
 */
class ScalingBenchmarkTest {

    private static final LocalTime DEPARTURE = LocalTime.of(8, 0);

    @Test
    void reportsHowTheOptimizerScales() {
        AppProperties properties = TestFixtures.properties(500, 200);
        RouteOptimizer optimizer =
                new RouteOptimizer(properties, TestFixtures.haversineProvider(properties));

        System.out.println("  stops |    ms | greedy km | 2-opt km | mejora");
        System.out.println("  ------+-------+-----------+----------+-------");

        long timeAt100 = 0;
        for (int stops : new int[] {10, 20, 50, 100, 200}) {
            List<RouteStop> route = randomStops(new Random(42), stops);

            // One untimed pass so JIT warm-up does not land on the measured run.
            optimizer.optimize(TestFixtures.DEPOT, "depot", route, DEPARTURE);

            long start = System.nanoTime();
            OptimizationResult result =
                    optimizer.optimize(TestFixtures.DEPOT, "depot", route, DEPARTURE);
            long millis = (System.nanoTime() - start) / 1_000_000;

            if (stops == 100) {
                timeAt100 = millis;
            }
            System.out.printf(
                    "  %5d | %5d | %9.1f | %8.1f | %5.1f%%%n",
                    stops,
                    millis,
                    result.initialDistanceMeters() / 1000,
                    result.totalDistanceMeters() / 1000,
                    result.improvementPercent());
        }

        // A hundred stops has to stay inside a request; if this trips, the loop shape changed.
        assertThat(timeAt100).isLessThan(10_000);
    }

    private static List<RouteStop> randomStops(Random random, int count) {
        List<RouteStop> stops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stops.add(new RouteStop(
                    (long) i,
                    "stop-" + i,
                    new Coordinate(19.30 + random.nextDouble() * 0.30, -99.30 + random.nextDouble() * 0.30),
                    Priority.values()[random.nextInt(3)],
                    null,
                    5));
        }
        return stops;
    }
}
