package com.routeopt.service;

import com.routeopt.ai.OrderParser;
import com.routeopt.ai.ParsedOrder;
import com.routeopt.ai.ParsedOrders;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.DeliveryOrder;
import com.routeopt.domain.DeliveryOrderRepository;
import com.routeopt.domain.GeocodeStatus;
import com.routeopt.domain.Priority;
import com.routeopt.geo.GeocodeResult;
import com.routeopt.geo.GeocodingService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns free-form text into persisted, geocoded orders, and maintains them afterwards. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderParser parser;
    private final GeocodingService geocoding;
    private final DeliveryOrderRepository repository;
    private final AppProperties properties;

    public OrderService(
            OrderParser parser,
            GeocodingService geocoding,
            DeliveryOrderRepository repository,
            AppProperties properties) {
        this.parser = parser;
        this.geocoding = geocoding;
        this.repository = repository;
        this.properties = properties;
    }

    /** Extraction only: nothing is geocoded or stored, so the user can review before committing. */
    public List<ParsedOrder> preview(String text) {
        return parser.parse(text).ordersOrEmpty();
    }

    @Transactional
    public List<DeliveryOrder> createFromText(String text) {
        ParsedOrders parsed = parser.parse(text);
        List<DeliveryOrder> created = new ArrayList<>();

        for (ParsedOrder parsedOrder : parsed.ordersOrEmpty()) {
            DeliveryOrder order = new DeliveryOrder();
            order.setRawText(text);
            order.setCustomerName(parsedOrder.customerName());
            order.setRawAddress(parsedOrder.address());
            order.setPriority(parsedOrder.priority());
            order.setTimeFrom(TimeParser.parse(parsedOrder.timeFrom()));
            order.setTimeTo(TimeParser.parse(parsedOrder.timeTo()));
            order.setNotes(parsedOrder.notes());
            order.setServiceMinutes(properties.routing().defaultServiceMinutes());

            resolveAddress(order);
            created.add(repository.save(order));
        }

        log.info("Created {} order(s) from {} characters of input", created.size(), text.length());
        return created;
    }

    /**
     * Creates a single order from already-structured fields, bypassing the model.
     *
     * <p>This is the manual-entry path for when the dispatcher already has a clean address — and
     * it is also what makes the deterministic half of the system usable and testable without
     * Anthropic credentials.
     */
    @Transactional
    public DeliveryOrder createManual(
            String address,
            String customerName,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String notes) {

        DeliveryOrder order = new DeliveryOrder();
        order.setRawText(address);
        order.setRawAddress(address);
        order.setCustomerName(customerName);
        order.setPriority(priority);
        order.setTimeFrom(timeFrom);
        order.setTimeTo(timeTo);
        order.setNotes(notes);
        order.setServiceMinutes(
                serviceMinutes == null ? properties.routing().defaultServiceMinutes() : serviceMinutes);

        resolveAddress(order);
        return repository.save(order);
    }

    @Transactional(readOnly = true)
    public List<DeliveryOrder> findAll() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public List<DeliveryOrder> findAllById(List<Long> ids) {
        List<DeliveryOrder> found = repository.findAllById(ids);
        if (found.size() != ids.size()) {
            List<Long> foundIds = found.stream().map(DeliveryOrder::getId).toList();
            List<Long> missing = ids.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new NoSuchElementException("Unknown order id(s): " + missing);
        }
        return found;
    }

    @Transactional
    public DeliveryOrder update(
            Long id,
            String address,
            Priority priority,
            LocalTime timeFrom,
            LocalTime timeTo,
            Integer serviceMinutes,
            String customerName,
            String notes) {

        DeliveryOrder order = repository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No order with id " + id));

        boolean addressChanged = address != null && !address.equals(order.getRawAddress());
        if (address != null) {
            order.setRawAddress(address);
        }
        if (priority != null) {
            order.setPriority(priority);
        }
        if (customerName != null) {
            order.setCustomerName(customerName);
        }
        if (notes != null) {
            order.setNotes(notes);
        }
        if (serviceMinutes != null) {
            order.setServiceMinutes(serviceMinutes);
        }
        order.setTimeFrom(timeFrom);
        order.setTimeTo(timeTo);

        if (addressChanged) {
            // A corrected address is the whole point of this endpoint, so re-resolve it.
            order.resetGeocoding();
            resolveAddress(order);
        }
        return repository.save(order);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("No order with id " + id);
        }
        repository.deleteById(id);
    }

    /** Retries geocoding for every order that does not currently have usable coordinates. */
    @Transactional
    public List<DeliveryOrder> retryFailedGeocoding() {
        List<DeliveryOrder> pending = new ArrayList<>();
        pending.addAll(repository.findByGeocodeStatus(GeocodeStatus.PENDING));
        pending.addAll(repository.findByGeocodeStatus(GeocodeStatus.FAILED));
        pending.addAll(repository.findByGeocodeStatus(GeocodeStatus.APPROXIMATE));
        pending.forEach(order -> {
            resolveAddress(order);
            repository.save(order);
        });
        return pending;
    }

    private void resolveAddress(DeliveryOrder order) {
        if (order.getRawAddress() == null || order.getRawAddress().isBlank()) {
            order.setGeocodeStatus(GeocodeStatus.NO_ADDRESS);
            return;
        }
        GeocodeResult result = geocoding.geocode(order.getRawAddress());
        if (result.found()) {
            order.setLat(result.coordinate().lat());
            order.setLon(result.coordinate().lon());
            order.setNormalizedAddress(result.displayName());
            order.setGeocodeStatus(result.exact() ? GeocodeStatus.OK : GeocodeStatus.APPROXIMATE);
        } else {
            order.setGeocodeStatus(GeocodeStatus.FAILED);
        }
    }
}
