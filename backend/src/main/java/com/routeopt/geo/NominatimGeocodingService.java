package com.routeopt.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import com.routeopt.domain.PostalAddress;
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
            return fromCache(cached.get());
        }

        // Walk from the address as written down to progressively simpler queries. Only the first
        // rung counts as an exact match; the rest found the street or the area, not the number.
        List<String> ladder = AddressQueries.ladder(address);
        for (int rung = 0; rung < ladder.size(); rung++) {
            String query = ladder.get(rung);
            NominatimPlace place = callNominatim(query);
            if (place == null) {
                continue;
            }

            double lat = Double.parseDouble(place.lat());
            double lon = Double.parseDouble(place.lon());
            boolean exact = rung == 0;
            cache.save(new GeocodeCacheEntry(
                    hash, normalized, place.displayName(), lat, lon, true, exact, query));

            if (exact) {
                return GeocodeResult.found(new Coordinate(lat, lon), place.displayName(), false);
            }
            log.info("Geocoded [{}] only after simplifying it to [{}]", address, query);
            return GeocodeResult.approximate(
                    new Coordinate(lat, lon), place.displayName(), false, query);
        }

        cache.save(new GeocodeCacheEntry(hash, normalized, null, null, null, false, false, null));
        log.info("No geocoding match for [{}] after {} attempt(s)", address, ladder.size());
        return GeocodeResult.notFound(false);
    }

    @Override
    @Transactional
    public GeocodeResult geocode(PostalAddress address) {
        if (address == null || !address.isGeocodable()) {
            return GeocodeResult.notFound(false);
        }

        String singleLine = address.toSingleLine();
        String hash = sha256("structured:" + normalize(singleLine));

        Optional<GeocodeCacheEntry> cached = cache.findById(hash);
        if (cached.isPresent()) {
            return fromCache(cached.get());
        }

        NominatimPlace place = callStructured(address);
        if (place != null) {
            double lat = Double.parseDouble(place.lat());
            double lon = Double.parseDouble(place.lon());
            cache.save(new GeocodeCacheEntry(
                    hash, normalize(singleLine), place.displayName(), lat, lon, true, true, singleLine));
            return GeocodeResult.found(new Coordinate(lat, lon), place.displayName(), false);
        }

        // The structured query is the better tool, but it is not infallible: it has no parameter
        // for a colonia, and an address whose parts were split wrongly still needs a way through.
        // Falling back to the free-form ladder costs one extra request and rescues those.
        log.info("Structured geocoding found nothing for [{}]; trying the free-form ladder", singleLine);
        return geocode(singleLine);
    }

    private NominatimPlace callStructured(PostalAddress address) {
        throttleLock.lock();
        try {
            waitForRateLimitWindow();
            List<NominatimPlace> results = restClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("limit", 1)
                                .queryParam("addressdetails", 0)
                                .queryParam("countrycodes", properties.geocoding().countryCodes());
                        // Structured parameters cannot be mixed with a free-form q, and the
                        // interior number is left out on purpose: it helps a driver, not a search.
                        addIfPresent(uriBuilder, "street", address.streetLine());
                        addIfPresent(uriBuilder, "postalcode", address.getPostalCode());
                        addIfPresent(uriBuilder, "city", address.getCity());
                        addIfPresent(uriBuilder, "state", address.getState());
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimPlace>>() {});
            return results == null || results.isEmpty() ? null : results.getFirst();
        } catch (RuntimeException ex) {
            log.warn("Structured geocoding request failed: {}", ex.getMessage());
            return null;
        } finally {
            lastRequestAt = System.currentTimeMillis();
            throttleLock.unlock();
        }
    }

    private static void addIfPresent(
            org.springframework.web.util.UriBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value);
        }
    }

    private GeocodeResult fromCache(GeocodeCacheEntry entry) {
        log.debug("Geocode cache hit for [{}]", entry.getQuery());
        if (!entry.isFound()) {
            return GeocodeResult.notFound(true);
        }
        Coordinate point = new Coordinate(entry.getLat(), entry.getLon());
        return entry.isExact()
                ? GeocodeResult.found(point, entry.getDisplayName(), true)
                : GeocodeResult.approximate(point, entry.getDisplayName(), true, entry.getMatchedQuery());
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
