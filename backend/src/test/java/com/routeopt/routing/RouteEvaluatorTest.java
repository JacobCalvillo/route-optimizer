package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Priority;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteEvaluatorTest {

    private final AppProperties properties = TestFixtures.properties(500, 200);

    @Test
    void waitingForAnEarlyWindowIsNotAViolation() {
        // A stop two blocks away that cannot be served before 11:00, departing at 08:00.
        List<RouteStop> stops = List.of(
                TestFixtures.stop("near", 19.4340, -99.1340, Priority.NORMAL, TestFixtures.between("11:00", "12:00")));
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

        RouteEvaluation evaluation =
                new RouteEvaluator(500, 0).evaluate(new int[] {0}, stops, matrix, LocalTime.of(8, 0));

        StopSchedule entry = evaluation.schedule().getFirst();
        assertThat(entry.waitMinutes()).isGreaterThan(150);
        assertThat(entry.lateMinutes()).isZero();
        assertThat(entry.arrival()).isBefore(LocalTime.of(11, 0));
        assertThat(evaluation.totalLateMinutes()).isZero();
    }

    @Test
    void arrivingAfterTheDeadlineAccumulatesLateness() {
        // Toluca is roughly 60 km away: unreachable by 08:30 at 30 km/h.
        List<RouteStop> stops = List.of(
                TestFixtures.stop("far", 19.2826, -99.6557, Priority.NORMAL, TestFixtures.before("08:30")));
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

        RouteEvaluation evaluation =
                new RouteEvaluator(500, 0).evaluate(new int[] {0}, stops, matrix, LocalTime.of(8, 0));

        assertThat(evaluation.schedule().getFirst().lateMinutes()).isPositive();
        assertThat(evaluation.totalLateMinutes()).isPositive();
        assertThat(evaluation.lateStopCount()).isEqualTo(1);
    }

    @Test
    void latenessRaisesCostAboveRawDistance() {
        List<RouteStop> stops = List.of(
                TestFixtures.stop("far", 19.2826, -99.6557, Priority.NORMAL, TestFixtures.before("08:30")));
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

        RouteEvaluator penalizing = new RouteEvaluator(500, 0);
        RouteEvaluation evaluation = penalizing.evaluate(new int[] {0}, stops, matrix, LocalTime.of(8, 0));

        assertThat(evaluation.cost())
                .isEqualTo(evaluation.totalDistanceMeters() + 500.0 * evaluation.totalLateMinutes());
        assertThat(evaluation.cost()).isGreaterThan(evaluation.totalDistanceMeters());
    }

    @Test
    void theTourReturnsToTheDepot() {
        List<RouteStop> stops = List.of(TestFixtures.stop("a", 19.44, -99.14));
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

        RouteEvaluation evaluation =
                new RouteEvaluator(0, 0).evaluate(new int[] {0}, stops, matrix, LocalTime.of(8, 0));

        // Out and back over the same leg.
        assertThat(evaluation.returnToDepotMeters()).isEqualTo(matrix.distance(1, 0));
        assertThat(evaluation.totalDistanceMeters()).isEqualTo(matrix.distance(0, 1) * 2);
    }

    @Test
    void serviceTimePushesLaterStopsBack() {
        RouteStop first = new RouteStop(
                null, "first", new com.routeopt.domain.Coordinate(19.44, -99.14), Priority.NORMAL, null, 30);
        RouteStop second = new RouteStop(
                null, "second", new com.routeopt.domain.Coordinate(19.45, -99.15), Priority.NORMAL, null, 0);
        List<RouteStop> stops = List.of(first, second);
        DistanceMatrix matrix = TestFixtures.matrixFor(stops, TestFixtures.DEPOT, properties);

        RouteEvaluation withService =
                new RouteEvaluator(0, 0).evaluate(new int[] {0, 1}, stops, matrix, LocalTime.of(8, 0));

        RouteStop firstNoService = new RouteStop(
                null, "first", new com.routeopt.domain.Coordinate(19.44, -99.14), Priority.NORMAL, null, 0);
        RouteEvaluation withoutService = new RouteEvaluator(0, 0)
                .evaluate(new int[] {0, 1}, List.of(firstNoService, second), matrix, LocalTime.of(8, 0));

        assertThat(withService.schedule().get(1).arrival())
                .isAfter(withoutService.schedule().get(1).arrival());
    }
}
