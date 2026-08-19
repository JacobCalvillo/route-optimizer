package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Priority;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteOptimizerTest {

    private static final LocalTime DEPARTURE = LocalTime.of(8, 0);

    private RouteOptimizer optimizerWith(AppProperties properties) {
        return new RouteOptimizer(properties, TestFixtures.haversineProvider(properties));
    }

    @Test
    void urgentStopsAreServedEarlierThanTheSameStopMarkedNormal() {
        AppProperties properties = TestFixtures.properties(500, 200);

        // "far" is the most distant stop, so pure distance would leave it for last.
        List<RouteStop> asNormal = List.of(
                TestFixtures.stop("near-a", 19.4400, -99.1400),
                TestFixtures.stop("near-b", 19.4200, -99.1200),
                TestFixtures.stop("near-c", 19.4450, -99.1250),
                TestFixtures.stop("far", 19.6000, -99.3000, Priority.NORMAL));

        List<RouteStop> asUrgent = List.of(
                TestFixtures.stop("near-a", 19.4400, -99.1400),
                TestFixtures.stop("near-b", 19.4200, -99.1200),
                TestFixtures.stop("near-c", 19.4450, -99.1250),
                TestFixtures.stop("far", 19.6000, -99.3000, Priority.URGENT));

        int normalPosition = positionOf(optimizerWith(properties)
                .optimize(TestFixtures.DEPOT, "depot", asNormal, DEPARTURE), "far");
        int urgentPosition = positionOf(optimizerWith(properties)
                .optimize(TestFixtures.DEPOT, "depot", asUrgent, DEPARTURE), "far");

        assertThat(urgentPosition).isLessThan(normalPosition);
    }

    @Test
    void aTightDeadlinePullsAStopForward() {
        AppProperties properties = TestFixtures.properties(500, 200);

        List<RouteStop> unconstrained = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.4200, -99.1200),
                TestFixtures.stop("c", 19.4600, -99.1600),
                TestFixtures.stop("deadline", 19.5200, -99.2200));

        List<RouteStop> withDeadline = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.4200, -99.1200),
                TestFixtures.stop("c", 19.4600, -99.1600),
                TestFixtures.stop("deadline", 19.5200, -99.2200, Priority.NORMAL, TestFixtures.before("08:45")));

        int without = positionOf(
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", unconstrained, DEPARTURE),
                "deadline");
        int with = positionOf(
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", withDeadline, DEPARTURE),
                "deadline");

        assertThat(with).isLessThanOrEqualTo(without);
    }

    @Test
    void reportsBothTheGreedyAndTheImprovedDistance() {
        AppProperties properties = TestFixtures.properties(500, 200);
        List<RouteStop> stops = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.4200, -99.1200),
                TestFixtures.stop("c", 19.4600, -99.1600),
                TestFixtures.stop("d", 19.3900, -99.1000),
                TestFixtures.stop("e", 19.5000, -99.2000));

        OptimizationResult result =
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", stops, DEPARTURE);

        assertThat(result.initialDistanceMeters()).isPositive();
        assertThat(result.totalDistanceMeters()).isPositive();
        assertThat(result.improvementPercent()).isGreaterThanOrEqualTo(0);
        assertThat(result.matrixProvider()).isEqualTo("haversine");
        assertThat(result.evaluation().schedule()).hasSize(stops.size());
    }

    @Test
    void everySequenceNumberIsAssignedOnce() {
        AppProperties properties = TestFixtures.properties(500, 200);
        List<RouteStop> stops = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.4200, -99.1200),
                TestFixtures.stop("c", 19.4600, -99.1600));

        OptimizationResult result =
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", stops, DEPARTURE);

        assertThat(result.evaluation().schedule().stream().map(StopSchedule::sequence).toList())
                .containsExactly(1, 2, 3);
        assertThat(result.evaluation().schedule().stream()
                        .map(entry -> entry.stop().label())
                        .distinct())
                .hasSize(3);
    }

    @Test
    void aMissedWindowProducesAWarningRatherThanAFailure() {
        AppProperties properties = TestFixtures.properties(500, 200);
        List<RouteStop> stops = List.of(
                // Toluca, ~60 km away, with a deadline that is physically impossible at 30 km/h.
                TestFixtures.stop("impossible", 19.2826, -99.6557, Priority.NORMAL, TestFixtures.before("08:15")),
                TestFixtures.stop("a", 19.4400, -99.1400));

        OptimizationResult result =
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", stops, DEPARTURE);

        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().getFirst()).contains("impossible");
        assertThat(result.evaluation().schedule()).hasSize(2);
    }

    @Test
    void warnsWhenAStopIsFarEnoughFromTheDepotToLookLikeAGeocodingError() {
        AppProperties properties = TestFixtures.properties(500, 200);
        List<RouteStop> stops = List.of(
                TestFixtures.stop("nearby", 19.4400, -99.1400),
                // What "Paseo de la Reforma 222" with no city actually resolved to: Playa del
                // Carmen, 1,300 km from a Mexico City depot.
                TestFixtures.stop("wrong-state", 20.6704, -87.0904));

        OptimizationResult result =
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", stops, DEPARTURE);

        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning)
                .contains("wrong-state")
                .contains("from the depot"));
        // Warned about, never excluded: the operator decides.
        assertThat(result.evaluation().schedule()).hasSize(2);
    }

    @Test
    void doesNotWarnAboutStopsWithinTheNormalServiceArea() {
        AppProperties properties = TestFixtures.properties(500, 200);
        List<RouteStop> stops = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.2826, -99.6557));

        OptimizationResult result =
                optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", stops, DEPARTURE);

        assertThat(result.warnings()).noneSatisfy(warning ->
                assertThat(warning).contains("from the depot"));
    }

    @Test
    void rejectsAnEmptyStopList() {
        AppProperties properties = TestFixtures.properties(500, 200);
        assertThatThrownBy(() ->
                        optimizerWith(properties).optimize(TestFixtures.DEPOT, "depot", List.of(), DEPARTURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no stops");
    }

    private static int positionOf(OptimizationResult result, String label) {
        return result.evaluation().schedule().stream()
                .filter(entry -> entry.stop().label().equals(label))
                .findFirst()
                .orElseThrow()
                .sequence();
    }
}
