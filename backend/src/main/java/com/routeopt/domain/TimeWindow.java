package com.routeopt.domain;

import java.time.LocalTime;

/**
 * Optional delivery window. Either bound may be null, meaning "unconstrained on that side".
 *
 * <p>{@code from} is a soft lower bound: arriving early means waiting, which is not a violation.
 * {@code to} is the bound that produces lateness penalties.
 */
public record TimeWindow(LocalTime from, LocalTime to) {

    public static final TimeWindow UNCONSTRAINED = new TimeWindow(null, null);

    public boolean isEmpty() {
        return from == null && to == null;
    }
}
