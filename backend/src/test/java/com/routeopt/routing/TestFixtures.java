package com.routeopt.routing;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import com.routeopt.domain.Priority;
import com.routeopt.domain.TimeWindow;
import java.time.LocalTime;
import java.util.List;

/** Shared builders so the routing tests stay readable. */
final class TestFixtures {

    /** Mexico City centre, used as the depot throughout. */
    static final Coordinate DEPOT = new Coordinate(19.4326, -99.1332);

    private TestFixtures() {}

    /**
     * One long shift by default, so tests about sequencing measure sequencing rather than the
     * split. Tests that care about shifts pass their own.
     */
    static AppProperties properties(double latePenalty, double priorityPenalty) {
        return properties(latePenalty, priorityPenalty, List.of(fullDay()));
    }

    static AppProperties.Shift fullDay() {
        return new AppProperties.Shift("Day", LocalTime.of(8, 0), 24);
    }

    static AppProperties.Shift shift(String name, String start, double hours) {
        return new AppProperties.Shift(name, LocalTime.parse(start), hours);
    }

    static AppProperties properties(
            double latePenalty, double priorityPenalty, List<AppProperties.Shift> shifts) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Ai("", "claude-sonnet-5", 8000, "LOW"),
                new AppProperties.Geocoding("http://localhost", "test", "mx", 0),
                new AppProperties.Routing(
                        "haversine",
                        1.3,
                        30,
                        5,
                        latePenalty,
                        priorityPenalty,
                        1000,
                        150,
                        24,
                        new AppProperties.Routing.Osrm("http://localhost", 10, "full")),
                shifts);
    }

    /** The single shift's evaluation, for tests that deal in one route. */
    static RouteEvaluation onlyShift(OptimizationResult result) {
        return result.shifts().getFirst().evaluation();
    }

    static DistanceMatrixProvider haversineProvider(AppProperties properties) {
        return new HaversineDistanceMatrixProvider(properties);
    }

    static RouteStop stop(String label, double lat, double lon) {
        return stop(label, lat, lon, Priority.NORMAL, TimeWindow.UNCONSTRAINED);
    }

    static RouteStop stop(String label, double lat, double lon, Priority priority) {
        return stop(label, lat, lon, priority, TimeWindow.UNCONSTRAINED);
    }

    static RouteStop stop(String label, double lat, double lon, Priority priority, TimeWindow window) {
        return new RouteStop(null, label, new Coordinate(lat, lon), priority, window, 0);
    }

    static TimeWindow before(String time) {
        return new TimeWindow(null, LocalTime.parse(time));
    }

    static TimeWindow between(String from, String to) {
        return new TimeWindow(LocalTime.parse(from), LocalTime.parse(to));
    }

    static DistanceMatrix matrixFor(List<RouteStop> stops, Coordinate depot, AppProperties properties) {
        List<Coordinate> points = new java.util.ArrayList<>();
        points.add(depot);
        stops.forEach(s -> points.add(s.coordinate()));
        return haversineProvider(properties).compute(points);
    }
}
