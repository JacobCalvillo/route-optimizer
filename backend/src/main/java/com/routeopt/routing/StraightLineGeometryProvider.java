package com.routeopt.routing;

import com.routeopt.domain.Coordinate;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The provider used when distances are Haversine approximations.
 *
 * <p>It deliberately returns nothing rather than echoing the stop coordinates back. Handing the
 * client "legs" that are just the stops would let the UI draw a solid line indistinguishable from a
 * real route. Returning empty makes the absence explicit, so the map can draw dashed segments and
 * label them as an approximation — which is what they are when distances come from Haversine.
 */
@Component
@ConditionalOnProperty(name = "app.routing.matrix", havingValue = "haversine")
public class StraightLineGeometryProvider implements RouteGeometryProvider {

    @Override
    public Optional<List<List<Coordinate>>> legsFor(List<Coordinate> orderedPoints) {
        return Optional.empty();
    }

    @Override
    public String name() {
        return "straight-line";
    }
}
