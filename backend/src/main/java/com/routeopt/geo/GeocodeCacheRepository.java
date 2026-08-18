package com.routeopt.geo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCacheEntry, String> {}
