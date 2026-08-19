package com.routeopt.api;

import com.routeopt.api.Dtos.OptimizeRequest;
import com.routeopt.api.Dtos.OptimizedRouteResponse;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import com.routeopt.domain.DeliveryOrder;
import com.routeopt.routing.OptimizationResult;
import com.routeopt.routing.RouteGeometryProvider;
import com.routeopt.routing.RouteOptimizer;
import com.routeopt.routing.RouteStop;
import com.routeopt.service.OrderService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final OrderService orders;
    private final RouteOptimizer optimizer;
    private final RouteGeometryProvider geometryProvider;
    private final AppProperties properties;

    public RouteController(
            OrderService orders,
            RouteOptimizer optimizer,
            RouteGeometryProvider geometryProvider,
            AppProperties properties) {
        this.orders = orders;
        this.optimizer = optimizer;
        this.geometryProvider = geometryProvider;
        this.properties = properties;
    }

    @PostMapping("/optimize")
    public OptimizedRouteResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        List<DeliveryOrder> candidates =
                request.orderIds() == null || request.orderIds().isEmpty()
                        ? orders.findAll()
                        : orders.findAllById(request.orderIds());

        List<RouteStop> stops = candidates.stream()
                .filter(DeliveryOrder::isRoutable)
                .map(this::toRouteStop)
                .toList();

        if (stops.isEmpty()) {
            throw new IllegalArgumentException(
                    "No routable orders: every selected order is missing coordinates. "
                            + "Fix the addresses or call POST /api/orders/geocode-retry first.");
        }

        Coordinate depot = new Coordinate(request.depot().lat(), request.depot().lon());
        OptimizationResult result = optimizer.optimize(
                depot,
                request.depot().label() == null ? "Depot" : request.depot().label(),
                stops,
                request.departureTime());

        // Geometry is asked for only after the order is known, and only for display. A failure
        // here degrades the drawing, never the route.
        List<Coordinate> geometry =
                geometryProvider.geometryFor(tourCoordinates(depot, result)).orElse(null);

        return OptimizedRouteResponse.from(result, request.departureTime(), geometry);
    }

    /** The full closed tour the optimizer chose: depot, every stop in order, then back to the depot. */
    private static List<Coordinate> tourCoordinates(Coordinate depot, OptimizationResult result) {
        List<Coordinate> points = new ArrayList<>();
        points.add(depot);
        result.evaluation().schedule().forEach(entry -> points.add(entry.stop().coordinate()));
        points.add(depot);
        return points;
    }

    private RouteStop toRouteStop(DeliveryOrder order) {
        int serviceMinutes = order.getServiceMinutes() == null
                ? properties.routing().defaultServiceMinutes()
                : order.getServiceMinutes();
        String label = order.getNormalizedAddress() != null
                ? order.getNormalizedAddress()
                : order.getRawAddress();
        return new RouteStop(
                order.getId(),
                order.getCustomerName() == null ? label : order.getCustomerName() + " - " + label,
                order.coordinate(),
                order.getPriority(),
                order.timeWindow(),
                serviceMinutes);
    }
}
