package com.routeopt.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Wrapper record: structured outputs require a top-level object, not a bare array. */
@JsonClassDescription("All delivery orders found in the input text")
public record ParsedOrders(
        @JsonPropertyDescription("One entry per delivery order found, in the order they appear")
                List<ParsedOrder> orders) {

    public List<ParsedOrder> ordersOrEmpty() {
        return orders == null ? List.of() : orders;
    }
}
