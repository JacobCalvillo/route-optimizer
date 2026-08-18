package com.routeopt.routing;

import java.util.List;

/**
 * The full evaluation of one candidate sequence.
 *
 * <p>{@code cost} is the penalized objective the local search minimizes; it is not a distance and
 * should never be shown to users. {@code totalDistanceMeters} is the real figure to display.
 */
public record RouteEvaluation(
        double cost,
        double totalDistanceMeters,
        double totalDurationSeconds,
        double returnToDepotMeters,
        long totalLateMinutes,
        List<StopSchedule> schedule) {

    public int lateStopCount() {
        return (int) schedule.stream().filter(StopSchedule::isLate).count();
    }
}
