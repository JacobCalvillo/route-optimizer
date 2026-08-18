package com.routeopt.api;

import com.routeopt.domain.DeliveryOrder;
import com.routeopt.domain.Priority;
import com.routeopt.routing.OptimizationResult;
import com.routeopt.routing.StopSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Request and response payloads for the REST API, grouped so the shape of the contract is visible. */
public final class Dtos {

    private Dtos() {}

    // --- requests ---------------------------------------------------------------------------

    public record OrderTextRequest(@NotBlank(message = "must not be empty") String text) {}

    /** Direct structured entry, for when the address is already clean and no parsing is needed. */
    public record ManualOrderRequest(
            @NotBlank(message = "must not be empty") String address,
            String customerName,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String notes) {}

    public record OrderUpdateRequest(
            String address,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String customerName,
            String notes) {}

    public record DepotRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
            String label) {}

    public record OptimizeRequest(
            @NotNull @Valid DepotRequest depot,
            @NotNull LocalTime departureTime,
            /* Null or empty means "every routable order currently on file". */
            List<Long> orderIds) {}

    // --- responses --------------------------------------------------------------------------

    public record OrderResponse(
            Long id,
            String customerName,
            String rawAddress,
            String normalizedAddress,
            Double lat,
            Double lon,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String geocodeStatus,
            String notes,
            Instant createdAt) {

        public static OrderResponse from(DeliveryOrder order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomerName(),
                    order.getRawAddress(),
                    order.getNormalizedAddress(),
                    order.getLat(),
                    order.getLon(),
                    order.getPriority(),
                    order.getTimeFrom(),
                    order.getTimeTo(),
                    order.getServiceMinutes(),
                    order.getGeocodeStatus().name(),
                    order.getNotes(),
                    order.getCreatedAt());
        }
    }

    /** A parsed-but-not-yet-stored order, returned by the preview endpoint. */
    public record ParsedOrderResponse(
            String customerName,
            String address,
            Priority priority,
            String timeFrom,
            String timeTo,
            String notes) {}

    public record RouteStopResponse(
            int sequence,
            Long orderId,
            String label,
            double lat,
            double lon,
            Priority priority,
            LocalTime eta,
            LocalTime departure,
            LocalTime timeFrom,
            LocalTime timeTo,
            long distanceFromPreviousMeters,
            long durationFromPreviousSeconds,
            long waitMinutes,
            long lateMinutes) {

        public static RouteStopResponse from(StopSchedule entry) {
            return new RouteStopResponse(
                    entry.sequence(),
                    entry.stop().orderId(),
                    entry.stop().label(),
                    entry.stop().coordinate().lat(),
                    entry.stop().coordinate().lon(),
                    entry.stop().priority(),
                    entry.arrival(),
                    entry.departure(),
                    entry.stop().timeWindow().from(),
                    entry.stop().timeWindow().to(),
                    Math.round(entry.distanceFromPreviousMeters()),
                    Math.round(entry.durationFromPreviousSeconds()),
                    entry.waitMinutes(),
                    entry.lateMinutes());
        }
    }

    public record OptimizedRouteResponse(
            double depotLat,
            double depotLon,
            String depotLabel,
            LocalTime departureTime,
            List<RouteStopResponse> stops,
            long totalDistanceMeters,
            long totalDurationSeconds,
            long returnToDepotMeters,
            /* Distance of the greedy tour, before 2-opt ran. */
            long initialDistanceMeters,
            double improvementPercent,
            int lateStopCount,
            long totalLateMinutes,
            String matrixProvider,
            List<String> warnings) {

        public static OptimizedRouteResponse from(OptimizationResult result, LocalTime departureTime) {
            return new OptimizedRouteResponse(
                    result.depot().lat(),
                    result.depot().lon(),
                    result.depotLabel(),
                    departureTime,
                    result.evaluation().schedule().stream().map(RouteStopResponse::from).toList(),
                    Math.round(result.totalDistanceMeters()),
                    Math.round(result.evaluation().totalDurationSeconds()),
                    Math.round(result.evaluation().returnToDepotMeters()),
                    Math.round(result.initialDistanceMeters()),
                    Math.round(result.improvementPercent() * 10) / 10.0,
                    result.evaluation().lateStopCount(),
                    result.evaluation().totalLateMinutes(),
                    result.matrixProvider(),
                    result.warnings());
        }
    }

    public record HealthResponse(
            String status, boolean aiParserAvailable, String model, String matrixProvider, long orderCount) {}
}
