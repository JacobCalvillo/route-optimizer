package com.routeopt.routing;

import com.routeopt.config.AppProperties;
import com.routeopt.config.AppProperties.Shift;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an optimized tour into one route per driver shift.
 *
 * <p>This is <em>route-first, cluster-second</em>: solve the whole delivery set as a single tour,
 * then cut that tour into vehicle routes. It is a long-standing VRP heuristic, and it earns its
 * place here for a specific reason — the giant tour already orders stops so that geographic
 * neighbours sit next to each other, so cutting it into consecutive runs yields shifts that each
 * cover a coherent area rather than criss-crossing the city.
 *
 * <p>Two refinements matter:
 *
 * <ul>
 *   <li>A shift's duration must include the drive <em>back</em> to the depot. A cut that ignores
 *       the return leg produces shifts that overrun by exactly the distance home.
 *   <li>Each shift is re-optimized after the cut, using <strong>its own start time</strong>. The
 *       giant tour was sequenced against a single clock, so an afternoon driver's delivery windows
 *       were evaluated against the wrong one. Re-running the local search per shift fixes that,
 *       and is cheap because each shift is a fraction of the whole.
 * </ul>
 *
 * <p>What does not fit in any shift is returned unscheduled rather than crammed in. A plan that
 * silently promises an eleven-hour day is worse than one that says which stops did not make it.
 */
public class ShiftPlanner {

    private final AppProperties properties;
    private final RouteEvaluator evaluator;

    public ShiftPlanner(AppProperties properties, RouteEvaluator evaluator) {
        this.properties = properties;
        this.evaluator = evaluator;
    }

    /**
     * @param sequence the optimized giant tour, as indices into {@code stops}
     * @return the per-shift plans, plus whatever did not fit
     */
    public Result plan(int[] sequence, List<RouteStop> stops, DistanceMatrix matrix) {
        List<Shift> shifts = properties.shifts();
        if (shifts == null || shifts.isEmpty()) {
            throw new IllegalStateException("No shifts configured; see app.shifts in application.yml");
        }

        List<Integer> remaining = new ArrayList<>();
        for (int index : sequence) {
            remaining.add(index);
        }

        LocalTime dayStart = shifts.getFirst().start();
        List<ShiftPlan> plans = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Shift shift : shifts) {
            if (remaining.isEmpty()) {
                break;
            }
            double budget = budgetHours(shift, dayStart);
            if (budget <= 0) {
                warnings.add("Shift %s starts after the %.0f-hour operating limit and was skipped."
                        .formatted(shift.name(), properties.routing().maxOperationalHours()));
                continue;
            }

            int take = largestPrefixWithin(remaining, stops, matrix, shift.start(), budget);
            if (take == 0) {
                // Not even one stop fits. Usually a single delivery further out than the whole
                // shift allows; saying so beats leaving the shift mysteriously empty.
                warnings.add("Shift %s could not fit even one more stop within %.1f hours."
                        .formatted(shift.name(), budget));
                continue;
            }

            int[] assigned = toArray(remaining.subList(0, take));
            // Re-optimize against this shift's own clock, which the giant tour could not know.
            int[] improved = TwoOptImprover.improve(
                    assigned, stops, matrix, shift.start(), evaluator,
                    properties.routing().maxIterations());

            plans.add(new ShiftPlan(
                    shift.name(),
                    shift.start(),
                    evaluator.evaluate(improved, stops, matrix, shift.start()),
                    stopsOf(improved, stops)));
            remaining.subList(0, take).clear();
        }

        List<RouteStop> unscheduled = remaining.stream().map(stops::get).toList();
        if (!unscheduled.isEmpty()) {
            warnings.add("%d stop(s) did not fit in the configured shifts and are not scheduled."
                    .formatted(unscheduled.size()));
        }
        return new Result(plans, unscheduled, List.copyOf(warnings));
    }

    /**
     * A shift may not run past the operating limit measured from the first shift's start, so its
     * usable budget is whichever ends sooner: its own length, or what is left of the day.
     */
    private double budgetHours(Shift shift, LocalTime dayStart) {
        double untilLimit = properties.routing().maxOperationalHours()
                - (minutesBetween(dayStart, shift.start()) / 60.0);
        return Math.min(shift.maxHours(), untilLimit);
    }

    /** How many leading stops fit in the budget, counting the drive back to the depot. */
    private int largestPrefixWithin(
            List<Integer> remaining,
            List<RouteStop> stops,
            DistanceMatrix matrix,
            LocalTime start,
            double budgetHours) {

        int fits = 0;
        for (int take = 1; take <= remaining.size(); take++) {
            // evaluate() closes the tour at the depot, so this budget already pays for going home.
            double hours = evaluator
                            .evaluate(toArray(remaining.subList(0, take)), stops, matrix, start)
                            .totalDurationSeconds()
                    / 3600.0;
            if (hours > budgetHours) {
                break;
            }
            fits = take;
        }
        return fits;
    }

    private static int[] toArray(List<Integer> indices) {
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<RouteStop> stopsOf(int[] sequence, List<RouteStop> stops) {
        List<RouteStop> ordered = new ArrayList<>(sequence.length);
        for (int index : sequence) {
            ordered.add(stops.get(index));
        }
        return List.copyOf(ordered);
    }

    private static long minutesBetween(LocalTime from, LocalTime to) {
        return java.time.Duration.between(from, to).toMinutes();
    }

    /** Everything the planner decided: the shifts that run, what fell out, and why. */
    public record Result(List<ShiftPlan> shifts, List<RouteStop> unscheduled, List<String> warnings) {}
}
