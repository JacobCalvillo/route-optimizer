package com.routeopt.geo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A resolved address, keyed by a hash of the normalized query.
 *
 * <p>Nominatim allows one request per second, so without this cache a batch of twenty orders would
 * take twenty seconds every single time it is re-routed.
 */
@Entity
@Table(name = "geocode_cache")
public class GeocodeCacheEntry {

    @Id
    @Column(length = 64)
    private String queryHash;

    @Column(length = 500)
    private String query;

    @Column(length = 500)
    private String displayName;

    private Double lat;
    private Double lon;

    /** False when the geocoder returned nothing, so a hopeless address is not retried every time. */
    @Column(nullable = false)
    private boolean found;

    @Column(nullable = false)
    private Instant fetchedAt = Instant.now();

    protected GeocodeCacheEntry() {}

    public GeocodeCacheEntry(
            String queryHash, String query, String displayName, Double lat, Double lon, boolean found) {
        this.queryHash = queryHash;
        this.query = query;
        this.displayName = displayName;
        this.lat = lat;
        this.lon = lon;
        this.found = found;
        this.fetchedAt = Instant.now();
    }

    public String getQueryHash() {
        return queryHash;
    }

    public String getQuery() {
        return query;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }

    public boolean isFound() {
        return found;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
