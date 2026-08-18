package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import com.routeopt.domain.Priority;
import com.routeopt.domain.TimeWindow;

/**
 * A stop as the optimizer sees it: coordinates plus the two constraints that shape the sequence.
 *
 * <p>Deliberately decoupled from the JPA entity so the algorithm can be unit-tested with plain
 * objects and no database.
 */
public record RouteStop(
        Long orderId,
        String label,
        Coordinate coordinate,
        Priority priority,
        TimeWindow timeWindow,
        int serviceMinutes) {

    public RouteStop {
        if (coordinate == null) {
            throw new IllegalArgumentException("A route stop needs coordinates: " + label);
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        if (timeWindow == null) {
            timeWindow = TimeWindow.UNCONSTRAINED;
        }
    }
}
