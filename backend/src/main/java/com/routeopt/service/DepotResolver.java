package com.routeopt.service;

import com.routeopt.domain.Coordinate;
import com.routeopt.domain.PostalAddress;
import com.routeopt.geo.GeocodeResult;
import com.routeopt.geo.GeocodingService;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns whatever the caller supplied for the depot into coordinates.
 *
 * <p>The depot used to be the one address in the system that had to be typed as latitude and
 * longitude, which was incoherent: every delivery address gets geocoded, so the origin should too.
 *
 * <p>Three input shapes are accepted, in this order of precedence:
 *
 * <ol>
 *   <li>explicit {@code lat} and {@code lon} — for programmatic callers that already know them
 *   <li>a string that <em>is</em> a coordinate pair, like {@code "19.4326, -99.1332"} — so one text
 *       field can serve both humans and pasted coordinates without a mode switch
 *   <li>anything else, geocoded through the same service and cache the deliveries use
 * </ol>
 */
@Service
public class DepotResolver {

    /** Two decimal numbers separated by a comma, and nothing else. */
    private static final Pattern COORDINATE_PAIR =
            Pattern.compile("^\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$");

    private final GeocodingService geocoding;

    public DepotResolver(GeocodingService geocoding) {
        this.geocoding = geocoding;
    }

    /** Structured resolution, which finds addresses the free-form search cannot. */
    public ResolvedDepot resolve(PostalAddress parts, String label) {
        if (parts == null || !parts.isGeocodable()) {
            throw new IllegalArgumentException("The depot needs at least a street, city or postal code.");
        }
        GeocodeResult result = geocoding.geocode(parts);
        if (!result.found()) {
            throw new IllegalArgumentException(
                    "Could not find the depot address: %s. Check the city and state."
                            .formatted(parts.toSingleLine()));
        }
        return new ResolvedDepot(
                result.coordinate(), labelOr(label, labelOr(result.displayName(), "Depot")));
    }

    public ResolvedDepot resolve(String address, Double lat, Double lon, String label) {
        if (lat != null && lon != null) {
            return new ResolvedDepot(new Coordinate(lat, lon), labelOr(label, "Depot"));
        }

        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException(
                    "The depot needs either an address or both lat and lon.");
        }

        Matcher pair = COORDINATE_PAIR.matcher(address);
        if (pair.matches()) {
            Coordinate parsed = new Coordinate(
                    Double.parseDouble(pair.group(1)), Double.parseDouble(pair.group(2)));
            return new ResolvedDepot(parsed, labelOr(label, "Depot"));
        }

        GeocodeResult result = geocoding.geocode(address);
        if (!result.found()) {
            throw new IllegalArgumentException(
                    "Could not find the depot address: %s. Add the city, or enter coordinates as "
                            .formatted(address)
                            + "\"lat, lon\".");
        }
        // Falling back to the geocoder's own display name means the map tooltip says where the
        // depot actually resolved to, not just what the user typed.
        return new ResolvedDepot(
                result.coordinate(), labelOr(label, labelOr(result.displayName(), "Depot")));
    }

    private static String labelOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** A depot with coordinates and a name fit to show on the map. */
    public record ResolvedDepot(Coordinate coordinate, String label) {}

    /** Exposed for the frontend hint text and for tests. */
    public static boolean looksLikeCoordinates(String value) {
        return value != null && COORDINATE_PAIR.matcher(value).matches();
    }

    @Override
    public String toString() {
        return "DepotResolver[%s]".formatted(geocoding.getClass().getSimpleName().toLowerCase(Locale.ROOT));
    }
}
