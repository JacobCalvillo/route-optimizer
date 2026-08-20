package com.routeopt.api;

import com.routeopt.domain.Coordinate;
import com.routeopt.domain.DeliveryOrder;
import com.routeopt.domain.Depot;
import com.routeopt.domain.PostalAddress;
import com.routeopt.domain.Priority;
import com.routeopt.routing.RouteStop;
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

    /**
     * An address in the parts a geocoder can use.
     *
     * <p>Every field optional: a form that only knows the street and city should still be able to
     * submit, and a wrong guessed city is worse than a missing one.
     */
    public record AddressRequest(
            String street,
            String exteriorNumber,
            String interiorNumber,
            String neighborhood,
            String postalCode,
            String city,
            String state) {

        public PostalAddress toPostalAddress() {
            return new PostalAddress(
                    street, exteriorNumber, interiorNumber, neighborhood, postalCode, city, state);
        }
    }

    /** Direct structured entry, for when the address is already known and no parsing is needed. */
    public record ManualOrderRequest(
            /* Either the parts, or a whole address as one string. */
            AddressRequest address,
            String rawAddress,
            String customerName,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String phone,
            String notes) {}

    public record OrderUpdateRequest(
            AddressRequest address,
            String rawAddress,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String customerName,
            String phone,
            String notes) {}

    public record SaveDepotRequest(
            @NotBlank(message = "must not be empty") String name,
            /* Either the parts, or a whole address as one string. */
            AddressRequest address,
            String rawAddress) {}

    /**
     * The depot: a saved one by id, explicit coordinates, or an address to geocode.
     *
     * <p>No field is required on its own, because bean validation cannot express "an id, or an
     * address, or both lat and lon". The resolver enforces that one usable combination arrived.
     */
    public record DepotRequest(
            /* A saved depot. Takes precedence over everything else and needs no geocoding. */
            Long id,
            String address,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lon,
            String label) {}

    public record OptimizeRequest(
            @NotNull @Valid DepotRequest depot,
            /* Optional. Only shapes the initial sequencing; each shift is re-timed to its own start. */
            LocalTime departureTime,
            /* Null or empty means "every routable order currently on file". */
            List<Long> orderIds) {}

    // --- responses --------------------------------------------------------------------------

    public record OrderResponse(
            Long id,
            String customerName,
            AddressResponse address,
            String phone,
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
                    AddressResponse.from(order.getAddress()),
                    order.getPhone(),
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

    /** The address as stored, both in parts and as one line. */
    public record AddressResponse(
            String street,
            String exteriorNumber,
            String interiorNumber,
            String neighborhood,
            String postalCode,
            String city,
            String state,
            String singleLine) {

        public static AddressResponse from(PostalAddress address) {
            return new AddressResponse(
                    address.getStreet(),
                    address.getExteriorNumber(),
                    address.getInteriorNumber(),
                    address.getNeighborhood(),
                    address.getPostalCode(),
                    address.getCity(),
                    address.getState(),
                    address.toSingleLine());
        }
    }

    /** A parsed-but-not-yet-stored order, returned by the preview endpoint. */
    public record ParsedOrderResponse(
            String customerName,
            AddressResponse address,
            Priority priority,
            String timeFrom,
            String timeTo,
            String phone,
            String references) {}

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

    /** One driver's route: their stops, their clock, and the road they drive. */
    public record ShiftResponse(
            String name,
            LocalTime start,
            LocalTime end,
            double hours,
            List<RouteStopResponse> stops,
            long totalDistanceMeters,
            long totalDurationSeconds,
            long returnToDepotMeters,
            int lateStopCount,
            /* One road polyline per leg; null when no road data was available. */
            List<List<double[]>> legs) {}

    /** A stop that no configured shift had room for, with the reason it fell out. */
    public record UnscheduledStopResponse(
            Long orderId, String label, double lat, double lon, Priority priority, String reason) {

        public static UnscheduledStopResponse from(RouteStop stop, String reason) {
            return new UnscheduledStopResponse(
                    stop.orderId(),
                    stop.label(),
                    stop.coordinate().lat(),
                    stop.coordinate().lon(),
                    stop.priority(),
                    reason);
        }
    }

    public record OptimizedRouteResponse(
            double depotLat,
            double depotLon,
            String depotLabel,
            List<ShiftResponse> shifts,
            List<UnscheduledStopResponse> unscheduled,
            long totalDistanceMeters,
            long totalDurationSeconds,
            /* Distance of the greedy giant tour, before 2-opt and before the split into shifts. */
            long initialDistanceMeters,
            /* The same tour after 2-opt, still uncut. improvementPercent compares these two. */
            long improvedTourDistanceMeters,
            /* Extra distance the split costs: one depot return per shift. */
            long splitOverheadMeters,
            double improvementPercent,
            int scheduledStopCount,
            int lateStopCount,
            long totalLateMinutes,
            String matrixProvider,
            String geometrySource,
            List<String> warnings) {}

    public record DepotResponse(
            Long id,
            String name,
            AddressResponse addressParts,
            String address,
            String normalizedAddress,
            double lat,
            double lon,
            Instant lastUsedAt,
            Instant createdAt) {

        public static DepotResponse from(Depot depot) {
            return new DepotResponse(
                    depot.getId(),
                    depot.getName(),
                    AddressResponse.from(depot.getPostalAddress()),
                    depot.getAddress(),
                    depot.getNormalizedAddress(),
                    depot.getLat(),
                    depot.getLon(),
                    depot.getLastUsedAt(),
                    depot.getCreatedAt());
        }
    }

    public record HealthResponse(
            String status,
            boolean aiParserAvailable,
            String model,
            String matrixProvider,
            long orderCount,
            /* Resolved location of the H2 file, which depends on the process working directory. */
            String databaseDirectory) {}
}
