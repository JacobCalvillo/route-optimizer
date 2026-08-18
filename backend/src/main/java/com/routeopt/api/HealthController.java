package com.routeopt.api;

import com.routeopt.ai.OrderParser;
import com.routeopt.api.Dtos.HealthResponse;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.DeliveryOrderRepository;
import com.routeopt.routing.RouteOptimizer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports whether the AI parser has credentials and which distance provider is wired in. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final OrderParser parser;
    private final RouteOptimizer optimizer;
    private final AppProperties properties;
    private final DeliveryOrderRepository repository;

    public HealthController(
            OrderParser parser,
            RouteOptimizer optimizer,
            AppProperties properties,
            DeliveryOrderRepository repository) {
        this.parser = parser;
        this.optimizer = optimizer;
        this.properties = properties;
        this.repository = repository;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                parser.isAvailable(),
                properties.ai().model(),
                optimizer.matrixProviderName(),
                repository.count());
    }
}
