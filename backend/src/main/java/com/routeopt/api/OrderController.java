package com.routeopt.api;

import com.routeopt.api.Dtos.AddressResponse;
import com.routeopt.api.Dtos.ManualOrderRequest;
import com.routeopt.api.Dtos.OrderResponse;
import com.routeopt.api.Dtos.OrderTextRequest;
import com.routeopt.api.Dtos.OrderUpdateRequest;
import com.routeopt.api.Dtos.ParsedOrderResponse;
import com.routeopt.domain.PostalAddress;
import com.routeopt.domain.Priority;
import com.routeopt.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Runs the AI extraction and returns the result without geocoding or storing anything. */
    @PostMapping("/parse")
    public List<ParsedOrderResponse> parse(@Valid @RequestBody OrderTextRequest request) {
        return orders.preview(request.text()).stream()
                .map(parsed -> new ParsedOrderResponse(
                        parsed.customerName(),
                        AddressResponse.from(new PostalAddress(
                                parsed.street(),
                                parsed.exteriorNumber(),
                                parsed.interiorNumber(),
                                parsed.neighborhood(),
                                parsed.postalCode(),
                                parsed.city(),
                                parsed.state())),
                        parsed.priority(),
                        parsed.timeFrom(),
                        parsed.timeTo(),
                        parsed.phone(),
                        parsed.references()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<OrderResponse> create(@Valid @RequestBody OrderTextRequest request) {
        return orders.createFromText(request.text()).stream().map(OrderResponse::from).toList();
    }

    /** Adds one order from structured fields, without going through the model. */
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createManual(@Valid @RequestBody ManualOrderRequest request) {
        return OrderResponse.from(orders.createManual(
                request.rawAddress(),
                request.address() == null ? null : request.address().toPostalAddress(),
                request.customerName(),
                request.priority() == null ? Priority.NORMAL : request.priority(),
                request.timeFrom(),
                request.timeTo(),
                request.serviceMinutes(),
                request.phone(),
                request.notes()));
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orders.findAll().stream().map(OrderResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @RequestBody OrderUpdateRequest request) {
        return OrderResponse.from(orders.update(
                id,
                request.rawAddress(),
                request.address() == null ? null : request.address().toPostalAddress(),
                request.priority(),
                request.timeFrom(),
                request.timeTo(),
                request.serviceMinutes(),
                request.customerName(),
                request.phone(),
                request.notes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orders.delete(id);
    }

    /** Re-attempts geocoding for orders that are still PENDING or FAILED. */
    @PostMapping("/geocode-retry")
    public List<OrderResponse> retryGeocoding() {
        return orders.retryFailedGeocoding().stream().map(OrderResponse::from).toList();
    }
}
