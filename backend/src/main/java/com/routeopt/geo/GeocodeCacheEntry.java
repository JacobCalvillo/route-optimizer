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

    /**
     * False when only a simplified form of the address matched.
     *
     * <p>Two things about this column are load-bearing. It is named explicitly because
     * {@code EXACT} is a reserved SQL keyword. And it is deliberately nullable: {@code ddl-auto:
     * update} cannot add a {@code NOT NULL} column to a table that already exists, and it only logs
     * a warning when the ALTER fails - the application starts happily and then fails on the first
     * query with "column not found". A primitive boolean reads a null as false, which is the right
     * default here anyway: not known to be exact.
     */
    @Column(name = "exact_match")
    private Boolean exact;

    /** The query that actually matched, when it was not the address as written. */
    @Column(length = 500)
    private String matchedQuery;

    @Column(nullable = false)
    private Instant fetchedAt = Instant.now();

    protected GeocodeCacheEntry() {}

    public GeocodeCacheEntry(
            String queryHash,
            String query,
            String displayName,
            Double lat,
            Double lon,
            boolean found,
            boolean exact,
            String matchedQuery) {
        this.queryHash = queryHash;
        this.query = query;
        this.displayName = displayName;
        this.lat = lat;
        this.lon = lon;
        this.found = found;
        this.exact = exact;
        this.matchedQuery = matchedQuery;
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

    /** Rows cached before this column existed read as null, which means "not known to be exact". */
    public boolean isExact() {
        return Boolean.TRUE.equals(exact);
    }

    public String getMatchedQuery() {
        return matchedQuery;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
