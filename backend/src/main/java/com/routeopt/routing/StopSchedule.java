package com.routeopt.routing;

import java.time.LocalTime;

/** The computed timing of one stop within a sequence. */
public record StopSchedule(
        RouteStop stop,
        int sequence,
        LocalTime arrival,
        LocalTime departure,
        double distanceFromPreviousMeters,
        double durationFromPreviousSeconds,
        long waitMinutes,
        long lateMinutes) {

    public boolean isLate() {
        return lateMinutes > 0;
    }
}
