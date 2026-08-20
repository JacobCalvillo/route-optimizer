package com.routeopt.api;

import com.routeopt.api.Dtos.OptimizeRequest;
import com.routeopt.api.Dtos.OptimizedRouteResponse;
import com.routeopt.api.Dtos.RouteStopResponse;
import com.routeopt.api.Dtos.ShiftResponse;
import com.routeopt.api.Dtos.UnscheduledStopResponse;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import com.routeopt.domain.DeliveryOrder;
import com.routeopt.routing.OptimizationResult;
import com.routeopt.routing.RouteGeometryProvider;
import com.routeopt.routing.RouteOptimizer;
import com.routeopt.routing.RouteStop;
import com.routeopt.routing.ShiftPlan;
import com.routeopt.service.DepotResolver;
import com.routeopt.service.DepotResolver.ResolvedDepot;
import com.routeopt.service.OrderService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.time.LocalTime;
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
    private final DepotResolver depotResolver;
    private final AppProperties properties;

    public RouteController(
            OrderService orders,
            RouteOptimizer optimizer,
            RouteGeometryProvider geometryProvider,
            DepotResolver depotResolver,
            AppProperties properties) {
        this.orders = orders;
        this.optimizer = optimizer;
        this.geometryProvider = geometryProvider;
        this.depotResolver = depotResolver;
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

        ResolvedDepot depot = depotResolver.resolve(
                request.depot().address(),
                request.depot().lat(),
                request.depot().lon(),
                request.depot().label());

        // Absent a requested start, sequence against the first shift's - the day really does begin
        // when the first driver leaves.
        LocalTime departure = request.departureTime() != null
                ? request.departureTime()
                : properties.shifts().getFirst().start();

        OptimizationResult result =
                optimizer.optimize(depot.coordinate(), depot.label(), stops, departure);

        return toResponse(result, depot.coordinate());
    }

    /**
     * Builds the response, asking for road geometry once per shift.
     *
     * <p>Geometry is fetched after the shifts are known and only for display, so a failure here
     * degrades the drawing and never the plan.
     */
    private OptimizedRouteResponse toResponse(OptimizationResult result, Coordinate depot) {
        List<ShiftResponse> shifts = new ArrayList<>();
        boolean anyGeometry = false;

        for (ShiftPlan shift : result.shifts()) {
            List<List<Coordinate>> legs =
                    geometryProvider.legsFor(tourCoordinates(depot, shift)).orElse(null);
            anyGeometry |= legs != null;

            shifts.add(new ShiftResponse(
                    shift.name(),
                    shift.start(),
                    shift.end(),
                    Math.round(shift.hours() * 10) / 10.0,
                    shift.evaluation().schedule().stream().map(RouteStopResponse::from).toList(),
                    Math.round(shift.evaluation().totalDistanceMeters()),
                    Math.round(shift.evaluation().totalDurationSeconds()),
                    Math.round(shift.evaluation().returnToDepotMeters()),
                    shift.evaluation().lateStopCount(),
                    legs == null ? null : toLatLon(legs)));
        }

        List<UnscheduledStopResponse> unscheduled = result.unscheduled().stream()
                .map(stop -> UnscheduledStopResponse.from(
                        stop, "No configured shift had room for it within its hours."))
                .toList();

        return new OptimizedRouteResponse(
                result.depot().lat(),
                result.depot().lon(),
                result.depotLabel(),
                List.copyOf(shifts),
                unscheduled,
                Math.round(result.totalDistanceMeters()),
                Math.round(result.totalDurationSeconds()),
                Math.round(result.initialDistanceMeters()),
                Math.round(result.improvedTourDistanceMeters()),
                Math.round(result.splitOverheadMeters()),
                Math.round(result.improvementPercent() * 10) / 10.0,
                result.totalStops(),
                result.lateStopCount(),
                result.totalLateMinutes(),
                result.matrixProvider(),
                anyGeometry ? "osrm" : null,
                result.warnings());
    }

    private static List<List<double[]>> toLatLon(List<List<Coordinate>> legs) {
        return legs.stream()
                .map(leg -> leg.stream()
                        .map(point -> new double[] {point.lat(), point.lon()})
                        .toList())
                .toList();
    }

    /** One shift's closed tour: depot, its stops in order, then back to the depot. */
    private static List<Coordinate> tourCoordinates(Coordinate depot, ShiftPlan shift) {
        List<Coordinate> points = new ArrayList<>();
        points.add(depot);
        shift.evaluation().schedule().forEach(entry -> points.add(entry.stop().coordinate()));
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
