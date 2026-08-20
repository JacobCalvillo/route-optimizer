package com.routeopt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A depot the dispatcher can pick again instead of retyping.
 *
 * <p>The resolved coordinates are stored, not just the address, so selecting a saved depot costs
 * nothing — no geocoding call, no waiting on Nominatim's one-per-second limit. Geocoding happens
 * once, when it is saved.
 */
@Entity
@Table(name = "depot")
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What the dispatcher calls it. Unique so the list stays unambiguous. */
    @Column(nullable = false, unique = true, length = 120)
    private String name;

    /** The address as typed, kept so it can be corrected and re-geocoded later. */
    @Column(length = 500)
    private String address;

    /** What the geocoder resolved it to, for display. */
    @Column(length = 500)
    private String normalizedAddress;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    /** Sorting key: the most recently used depot is the one most likely wanted next. */
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Depot() {}

    public Depot(String name, String address, String normalizedAddress, double lat, double lon) {
        this.name = name;
        this.address = address;
        this.normalizedAddress = normalizedAddress;
        this.lat = lat;
        this.lon = lon;
    }

    public Coordinate coordinate() {
        return new Coordinate(lat, lon);
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNormalizedAddress() {
        return normalizedAddress;
    }

    public void setNormalizedAddress(String normalizedAddress) {
        this.normalizedAddress = normalizedAddress;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
