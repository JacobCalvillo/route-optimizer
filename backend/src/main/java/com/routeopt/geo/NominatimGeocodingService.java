package com.routeopt.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Geocoding against the public Nominatim instance.
 *
 * <p>Two things are mandatory under Nominatim's usage policy and are implemented here: an
 * identifying User-Agent header, and at most one request per second. The rate limit is enforced
 * with a process-wide lock, which is why every network lookup goes through {@link #callNominatim}.
 */
@Service
public class NominatimGeocodingService implements GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingService.class);

    private final AppProperties properties;
    private final GeocodeCacheRepository cache;
    private final RestClient restClient;

    private final ReentrantLock throttleLock = new ReentrantLock();
    private long lastRequestAt = 0L;

    public NominatimGeocodingService(AppProperties properties, GeocodeCacheRepository cache) {
        this.properties = properties;
        this.cache = cache;
        this.restClient = RestClient.builder()
                .baseUrl(properties.geocoding().baseUrl())
                .defaultHeader("User-Agent", properties.geocoding().userAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    @Transactional
    public GeocodeResult geocode(String address) {
        if (address == null || address.isBlank()) {
            return GeocodeResult.notFound(false);
        }

        String normalized = normalize(address);
        String hash = sha256(normalized);

        Optional<GeocodeCacheEntry> cached = cache.findById(hash);
        if (cached.isPresent()) {
            GeocodeCacheEntry entry = cached.get();
            log.debug("Geocode cache hit for [{}]", normalized);
            return entry.isFound()
                    ? GeocodeResult.found(
                            new Coordinate(entry.getLat(), entry.getLon()), entry.getDisplayName(), true)
                    : GeocodeResult.notFound(true);
        }

        NominatimPlace place = callNominatim(address);
        if (place == null) {
            cache.save(new GeocodeCacheEntry(hash, normalized, null, null, null, false));
            log.info("No geocoding match for [{}]", address);
            return GeocodeResult.notFound(false);
        }

        double lat = Double.parseDouble(place.lat());
        double lon = Double.parseDouble(place.lon());
        cache.save(new GeocodeCacheEntry(hash, normalized, place.displayName(), lat, lon, true));
        return GeocodeResult.found(new Coordinate(lat, lon), place.displayName(), false);
    }

    private NominatimPlace callNominatim(String address) {
        throttleLock.lock();
        try {
            waitForRateLimitWindow();
            List<NominatimPlace> results = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("addressdetails", 0)
                            .queryParam("countrycodes", properties.geocoding().countryCodes())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimPlace>>() {});
            return results == null || results.isEmpty() ? null : results.getFirst();
        } catch (RuntimeException ex) {
            log.warn("Geocoding request failed for [{}]: {}", address, ex.getMessage());
            return null;
        } finally {
            lastRequestAt = System.currentTimeMillis();
            throttleLock.unlock();
        }
    }

    /** Sleeps just long enough that consecutive calls stay under Nominatim's one-per-second limit. */
    private void waitForRateLimitWindow() {
        long minInterval = properties.geocoding().minIntervalMillis();
        long elapsed = System.currentTimeMillis() - lastRequestAt;
        if (lastRequestAt > 0 && elapsed < minInterval) {
            try {
                Thread.sleep(minInterval - elapsed);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String normalize(String address) {
        return address.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimPlace(String lat, String lon, @JsonProperty("display_name") String displayName) {}
}
