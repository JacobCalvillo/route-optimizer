package com.routeopt.routing;

import com.routeopt.domain.TimeWindow;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a candidate sequence forward in time and scores it.
 *
 * <p>This is the heart of the optimizer. A plain TSP would minimize distance alone, which would
 * silently ignore both requirements of this system: urgent orders should go early, and delivery
 * windows should be met. So the objective is a penalized cost, expressed entirely in metres so the
 * three terms are comparable:
 *
 * <pre>
 *   cost = totalDistance
 *        + latePenaltyPerMinute        * Σ lateMinutes(stop)
 *        + priorityPenaltyPerPosition  * Σ priorityWeight(stop) * position(stop)
 * </pre>
 *
 * <p>The priority term is what pulls urgent stops toward the front: an URGENT stop (weight 3.0)
 * sitting in position 8 costs six times what it would in position 4, while a LOW stop (weight 0.3)
 * barely cares where it lands. Nothing here makes a route infeasible — a sequence that misses a
 * window is scored worse, not rejected, so the user always gets a route plus an explicit list of
 * which promises it breaks.
 */
public class RouteEvaluator {

    private final double latePenaltyPerMinute;
    private final double priorityPenaltyPerPosition;

    public RouteEvaluator(double latePenaltyPerMinute, double priorityPenaltyPerPosition) {
        this.latePenaltyPerMinute = latePenaltyPerMinute;
        this.priorityPenaltyPerPosition = priorityPenaltyPerPosition;
    }

    /**
     * Scores one sequence.
     *
     * @param sequence indices into {@code stops}, each appearing exactly once
     * @param stops the stops, in their original (matrix) order
     * @param matrix travel costs; index 0 is the depot, stop {@code i} is matrix index {@code i+1}
     * @param departureTime when the vehicle leaves the depot
     */
    public RouteEvaluation evaluate(
            int[] sequence, List<RouteStop> stops, DistanceMatrix matrix, LocalTime departureTime) {

        List<StopSchedule> schedule = new ArrayList<>(sequence.length);

        double totalDistance = 0;
        double totalDuration = 0;
        double priorityPenalty = 0;
        long totalLateMinutes = 0;

        // Time is tracked as minutes since midnight so it can be compared against the windows.
        double clock = toMinutes(departureTime);
        int previousMatrixIndex = 0; // the depot

        for (int position = 0; position < sequence.length; position++) {
            int stopIndex = sequence[position];
            RouteStop stop = stops.get(stopIndex);
            int matrixIndex = stopIndex + 1;

            double legMeters = matrix.distance(previousMatrixIndex, matrixIndex);
            double legSeconds = matrix.duration(previousMatrixIndex, matrixIndex);
            totalDistance += legMeters;
            totalDuration += legSeconds;

            clock += legSeconds / 60.0;
            double arrival = clock;

            TimeWindow window = stop.timeWindow();
            long waitMinutes = 0;
            if (window.from() != null && clock < toMinutes(window.from())) {
                // Arriving early is not a violation: the driver simply waits.
                waitMinutes = Math.round(toMinutes(window.from()) - clock);
                clock = toMinutes(window.from());
            }

            long lateMinutes = 0;
            if (window.to() != null && clock > toMinutes(window.to())) {
                lateMinutes = Math.round(clock - toMinutes(window.to()));
                totalLateMinutes += lateMinutes;
            }

            double serviceStart = clock;
            clock += stop.serviceMinutes();
            totalDuration += stop.serviceMinutes() * 60.0;

            // Positions are 1-based so the first stop still carries a (small) priority cost and the
            // relative ordering of two urgent stops is scored.
            priorityPenalty += stop.priority().weight() * (position + 1);

            schedule.add(new StopSchedule(
                    stop,
                    position + 1,
                    toLocalTime(arrival),
                    toLocalTime(serviceStart + stop.serviceMinutes()),
                    legMeters,
                    legSeconds,
                    waitMinutes,
                    lateMinutes));

            previousMatrixIndex = matrixIndex;
        }

        // Close the tour: the vehicle goes back to the depot.
        double returnMeters = matrix.distance(previousMatrixIndex, 0);
        totalDistance += returnMeters;
        totalDuration += matrix.duration(previousMatrixIndex, 0);

        double cost = totalDistance
                + latePenaltyPerMinute * totalLateMinutes
                + priorityPenaltyPerPosition * priorityPenalty;

        return new RouteEvaluation(
                cost, totalDistance, totalDuration, returnMeters, totalLateMinutes, List.copyOf(schedule));
    }

    private static double toMinutes(LocalTime time) {
        return time.getHour() * 60.0 + time.getMinute() + time.getSecond() / 60.0;
    }

    /** Wraps past midnight rather than throwing, so an overlong route still produces a schedule. */
    private static LocalTime toLocalTime(double minutesSinceMidnight) {
        long minutes = Math.round(minutesSinceMidnight);
        long normalized = ((minutes % 1440) + 1440) % 1440;
        return LocalTime.of((int) (normalized / 60), (int) (normalized % 60));
    }
}
