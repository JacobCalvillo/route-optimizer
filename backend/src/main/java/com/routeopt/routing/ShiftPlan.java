package com.routeopt.routing;

import java.time.LocalTime;
import java.util.List;

/** One driver's assigned work: the stops, when they run, and what it costs. */
public record ShiftPlan(
        String name,
        LocalTime start,
        RouteEvaluation evaluation,
        List<RouteStop> stops) {

    public LocalTime end() {
        return evaluation.schedule().isEmpty()
                ? start
                : start.plusMinutes(Math.round(evaluation.totalDurationSeconds() / 60.0));
    }

    public double hours() {
        return evaluation.totalDurationSeconds() / 3600.0;
    }
}
