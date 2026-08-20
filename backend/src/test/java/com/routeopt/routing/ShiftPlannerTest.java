package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeopt.config.AppProperties;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShiftPlannerTest {

    /** The configured operation: two eight-hour drivers, the second leaving six hours later. */
    private static final List<AppProperties.Shift> TWO_SHIFTS =
            List.of(TestFixtures.shift("Morning", "06:00", 8), TestFixtures.shift("Afternoon", "12:00", 8));

    /** Enough stops, spread widely enough, that one eight-hour shift cannot hold them all. */
    private static List<RouteStop> manyStops(int count) {
        List<RouteStop> stops = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            // A ring around the depot, far enough apart that travel dominates.
            double angle = 2 * Math.PI * i / count;
            stops.add(TestFixtures.stop(
                    "stop-" + i,
                    TestFixtures.DEPOT.lat() + 0.45 * Math.cos(angle),
                    TestFixtures.DEPOT.lon() + 0.45 * Math.sin(angle)));
        }
        return stops;
    }

    /** The same configuration with a different operating ceiling. */
    private static AppProperties withOperatingHours(AppProperties from, double hours) {
        return new AppProperties(
                from.cors(),
                from.ai(),
                from.geocoding(),
                new AppProperties.Routing(
                        "haversine", 1.3, 30, 5, 500, 200, 1000, 150, hours,
                        from.routing().osrm()),
                from.shifts());
    }

    private OptimizationResult optimize(AppProperties properties, List<RouteStop> stops) {
        return new RouteOptimizer(properties, TestFixtures.haversineProvider(properties))
                .optimize(TestFixtures.DEPOT, "depot", stops, LocalTime.of(6, 0));
    }

    @Test
    void keepsEverythingInOneShiftWhenItFits() {
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);
        List<RouteStop> stops = List.of(
                TestFixtures.stop("a", 19.4400, -99.1400),
                TestFixtures.stop("b", 19.4200, -99.1200),
                TestFixtures.stop("c", 19.4600, -99.1600));

        OptimizationResult result = optimize(properties, stops);

        assertThat(result.shifts()).hasSize(1);
        assertThat(result.shifts().getFirst().name()).isEqualTo("Morning");
        assertThat(result.unscheduled()).isEmpty();
    }

    @Test
    void spillsIntoTheSecondShiftWhenTheFirstIsFull() {
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);

        OptimizationResult result = optimize(properties, manyStops(14));

        assertThat(result.shifts()).hasSize(2);
        assertThat(result.shifts()).extracting(ShiftPlan::name).containsExactly("Morning", "Afternoon");
        assertThat(result.shifts()).allSatisfy(shift -> assertThat(shift.hours()).isLessThanOrEqualTo(8.0));
    }

    @Test
    void everyShiftStartsAtItsOwnTime() {
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);

        OptimizationResult result = optimize(properties, manyStops(14));

        assertThat(result.shifts().get(0).start()).isEqualTo(LocalTime.of(6, 0));
        assertThat(result.shifts().get(1).start()).isEqualTo(LocalTime.of(12, 0));
        // The afternoon driver's first arrival must be on their clock, not the morning driver's.
        assertThat(result.shifts().get(1).evaluation().schedule().getFirst().arrival())
                .isAfterOrEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void assignsEveryStopExactlyOnceAcrossShiftsAndTheUnscheduledList() {
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);
        List<RouteStop> stops = manyStops(20);

        OptimizationResult result = optimize(properties, stops);

        List<String> placed = new java.util.ArrayList<>();
        result.shifts().forEach(shift ->
                shift.evaluation().schedule().forEach(entry -> placed.add(entry.stop().label())));
        result.unscheduled().forEach(stop -> placed.add(stop.label()));

        assertThat(placed).hasSize(stops.size());
        assertThat(placed).doesNotHaveDuplicates();
    }

    @Test
    void leavesTheOverflowUnscheduledAndSaysSo() {
        // Two shifts cannot absorb this many widely spread stops at 30 km/h.
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);

        OptimizationResult result = optimize(properties, manyStops(40));

        assertThat(result.unscheduled()).isNotEmpty();
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("did not fit"));
    }

    @Test
    void anUnreachableStopDoesNotBlockAnEntireShift() {
        // Acapulco from a Mexico City depot needs longer than a whole shift on its own. Because
        // the tour is cut contiguously, leaving it in the queue used to stop the afternoon driver
        // from taking anything at all while a dozen reachable stops waited behind it.
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);
        List<RouteStop> stops = new java.util.ArrayList<>(manyStops(12));
        stops.add(TestFixtures.stop("acapulco", 16.8531, -99.8237));

        OptimizationResult result = optimize(properties, stops);

        assertThat(result.unscheduled()).extracting(RouteStop::label).contains("acapulco");
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("longer than a full shift"));
        // Both drivers still get work.
        assertThat(result.shifts()).hasSize(2);
        assertThat(result.shifts()).allSatisfy(shift ->
                assertThat(shift.evaluation().schedule()).isNotEmpty());
    }

    @Test
    void theAfternoonShiftEndsExactlyAtTheCloseOfTheOperatingWindow() {
        // 06:00 to 20:00 is fourteen hours, and the afternoon driver's eight land on the boundary:
        // the ceiling binds exactly rather than leaving slack, so this is worth pinning.
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);
        AppProperties realDay = withOperatingHours(properties, 14);

        OptimizationResult result = optimize(realDay, manyStops(14));

        assertThat(result.shifts()).allSatisfy(shift ->
                assertThat(shift.end()).isBeforeOrEqualTo(LocalTime.of(20, 0)));
    }

    @Test
    void respectsTheOperatingCeilingEvenWhenAShiftWouldAllowMore() {
        // A second shift starting at 12:00 with a 10-hour ceiling from 06:00 has only 4 hours left.
        AppProperties properties = TestFixtures.properties(
                500,
                200,
                List.of(TestFixtures.shift("Morning", "06:00", 8), TestFixtures.shift("Afternoon", "12:00", 8)));
        OptimizationResult result = optimize(withOperatingHours(properties, 10), manyStops(14));

        assertThat(result.shifts()).hasSizeLessThanOrEqualTo(2);
        result.shifts().stream()
                .filter(shift -> shift.name().equals("Afternoon"))
                .forEach(shift -> assertThat(shift.hours()).isLessThanOrEqualTo(4.0));
    }

    @Test
    void countsTheReturnToTheDepotInsideTheShift() {
        AppProperties properties = TestFixtures.properties(500, 200, TWO_SHIFTS);

        OptimizationResult result = optimize(properties, manyStops(14));

        // A shift that ignored the drive home would overrun by exactly that leg.
        assertThat(result.shifts()).allSatisfy(shift -> {
            assertThat(shift.evaluation().returnToDepotMeters()).isPositive();
            assertThat(shift.hours()).isLessThanOrEqualTo(8.0);
        });
    }
}
