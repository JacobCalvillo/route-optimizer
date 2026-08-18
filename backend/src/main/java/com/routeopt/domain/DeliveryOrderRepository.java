package com.routeopt.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    List<DeliveryOrder> findAllByOrderByCreatedAtAsc();

    List<DeliveryOrder> findByGeocodeStatus(GeocodeStatus status);
}
