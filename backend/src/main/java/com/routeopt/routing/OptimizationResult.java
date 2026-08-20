package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import java.util.List;

/**
 * The optimizer's output: one route per driver shift, plus the "before" figures.
 *
 * <p>{@code initialDistanceMeters} is the greedy giant tour's distance and
 * {@code totalDistanceMeters} is what is actually driven once the tour has been improved and split
 * across shifts. Keeping both is what lets the UI show that the local search did something rather
 * than just asserting it - though note the split adds one depot return per shift, so the final
 * figure pays for those.
 */
public record OptimizationResult(
        Coordinate depot,
        String depotLabel,
        List<ShiftPlan> shifts,
        List<RouteStop> unscheduled,
        double initialDistanceMeters,
        /* The single tour after 2-opt, before it was cut into shifts. */
        double improvedTourDistanceMeters,
        double initialCost,
        String matrixProvider,
        List<String> warnings) {

    public double totalDistanceMeters() {
        return shifts.stream().mapToDouble(s -> s.evaluation().totalDistanceMeters()).sum();
    }

    public double totalDurationSeconds() {
        return shifts.stream().mapToDouble(s -> s.evaluation().totalDurationSeconds()).sum();
    }

    public int totalStops() {
        return shifts.stream().mapToInt(s -> s.evaluation().schedule().size()).sum();
    }

    public int lateStopCount() {
        return shifts.stream().mapToInt(s -> s.evaluation().lateStopCount()).sum();
    }

    public long totalLateMinutes() {
        return shifts.stream().mapToLong(s -> s.evaluation().totalLateMinutes()).sum();
    }

    /**
     * What the local search saved, measured on the single tour before the split.
     *
     * <p>Comparing the greedy tour against {@link #totalDistanceMeters()} would understate it to
     * the point of absurdity: splitting into shifts adds one depot return per driver, so the
     * distance actually driven is normally *higher* than the uncut tour. Those are two different
     * questions, and conflating them once produced a headline "0% improvement" on a plan where
     * 2-opt had in fact done its job.
     */
    public double improvementPercent() {
        if (initialDistanceMeters <= 0) {
            return 0;
        }
        return Math.max(
                0,
                (initialDistanceMeters - improvedTourDistanceMeters) / initialDistanceMeters * 100.0);
    }

    /** Extra distance the shift split costs, as the depot returns it adds. */
    public double splitOverheadMeters() {
        return Math.max(0, totalDistanceMeters() - improvedTourDistanceMeters);
    }
}
