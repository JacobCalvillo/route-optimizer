package com.routeopt.domain;

/**
 * Delivery urgency extracted from the free-form order text.
 *
 * <p>The weight drives two parts of the optimizer: the nearest-neighbour greedy pass divides
 * candidate distances by it (so urgent stops win ties against closer but relaxed ones), and the
 * penalized cost function multiplies it by the stop's position in the sequence.
 */
public enum Priority {
    URGENT(3.0),
    NORMAL(1.0),
    LOW(0.3);

    private final double weight;

    Priority(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
