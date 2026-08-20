package com.routeopt.geo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;

/**
 * Builds the ordered list of queries to try for one address.
 *
 * <p>Real Mexican addresses fail free-form geocoding for reasons that have nothing to do with the
 * street being absent from the map. A dispatcher writes
 * {@code "Fracc. Felipe Tena Ramirez 101, Praderas de Huinala, 66642 Loma La paz, N.L."} and
 * Nominatim returns nothing — yet the street is in OpenStreetMap, and
 * {@code "Felipe Tena Ramirez 101, Praderas de Huinala"} finds it immediately.
 *
 * <p>What that example teaches is why a single transformation is not enough: removing the
 * {@code Fracc.} prefix alone still failed, and trimming the tail alone still failed. Only doing
 * both worked. So this produces a ladder — progressively cleaner and shorter — and the geocoder
 * walks it until something matches.
 *
 * <p>The trade-off is precision. A later rung resolves to the street or the neighbourhood rather
 * than the exact number, which is usually fine for sequencing stops but must never be passed off
 * as an exact match. {@link GeocodeResult#exact()} carries that distinction outward.
 */
public final class AddressQueries {

    /** Beyond this the queries are too vague to be worth a request against the rate limit. */
    private static final int MAX_ATTEMPTS = 5;

    /** Segment prefixes that describe a *kind* of place and only add noise to a free-form search. */
    private static final List<String> NOISE_PREFIXES =
            List.of("fracc.", "fracc", "fraccionamiento", "col.", "colonia", "cond.", "condominio",
                    "priv.", "privada", "unidad habitacional", "u.h.");

    /**
     * State abbreviations, expanded because Nominatim matches the full name far more reliably.
     *
     * <p>{@code Col.} is deliberately absent: in a Mexican address it means "colonia" many times
     * more often than "Colima", and it is handled as a noise prefix above.
     */
    private static final Map<String, String> STATES = Map.ofEntries(
            Map.entry("n.l.", "Nuevo León"),
            Map.entry("nl", "Nuevo León"),
            Map.entry("edo. mex.", "Estado de México"),
            Map.entry("edomex", "Estado de México"),
            Map.entry("cdmx", "Ciudad de México"),
            Map.entry("d.f.", "Ciudad de México"),
            Map.entry("b.c.", "Baja California"),
            Map.entry("b.c.s.", "Baja California Sur"),
            Map.entry("q. roo", "Quintana Roo"),
            Map.entry("q.roo", "Quintana Roo"),
            Map.entry("s.l.p.", "San Luis Potosí"),
            Map.entry("jal.", "Jalisco"),
            Map.entry("ver.", "Veracruz"),
            Map.entry("gto.", "Guanajuato"),
            Map.entry("qro.", "Querétaro"),
            Map.entry("mich.", "Michoacán"),
            Map.entry("chih.", "Chihuahua"),
            Map.entry("coah.", "Coahuila"),
            Map.entry("tamps.", "Tamaulipas"),
            Map.entry("sin.", "Sinaloa"),
            Map.entry("son.", "Sonora"),
            Map.entry("pue.", "Puebla"),
            Map.entry("gro.", "Guerrero"),
            Map.entry("oax.", "Oaxaca"),
            Map.entry("chis.", "Chiapas"),
            Map.entry("yuc.", "Yucatán"));

    private AddressQueries() {}

    /**
     * @return the queries to try, most precise first, without duplicates
     */
    public static List<String> ladder(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }

        // LinkedHashSet keeps the order while dropping rungs that collapse onto an earlier one -
        // an address with no abbreviations produces the same string twice otherwise.
        SequencedSet<String> queries = new LinkedHashSet<>();
        queries.add(address.trim());

        List<String> segments = cleanSegments(address);
        if (!segments.isEmpty()) {
            queries.add(String.join(", ", segments));
            // Drop one trailing segment at a time. The noise in a written address accumulates at
            // the end: postal codes, mistaken localities, state abbreviations. Never go below two
            // segments, because a bare street name matches half the country.
            for (int keep = segments.size() - 1; keep >= 2; keep--) {
                queries.add(String.join(", ", segments.subList(0, keep)));
            }
        }

        return queries.stream().filter(q -> !q.isBlank()).limit(MAX_ATTEMPTS).toList();
    }

    /** Splits on commas and strips each segment of the noise that defeats a free-form search. */
    private static List<String> cleanSegments(String address) {
        List<String> cleaned = new ArrayList<>();
        for (String raw : address.split(",")) {
            String segment = stripPostalCodes(raw).trim();
            segment = stripNoisePrefix(segment);
            segment = expandState(segment);
            if (!segment.isBlank()) {
                cleaned.add(segment.trim());
            }
        }
        return cleaned;
    }

    /** Removes standalone five-digit groups; they are as often wrong as they are helpful. */
    private static String stripPostalCodes(String segment) {
        return segment.replaceAll("\\b\\d{5}\\b", " ").replaceAll("\\s{2,}", " ");
    }

    private static String stripNoisePrefix(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        for (String prefix : NOISE_PREFIXES) {
            if (lower.startsWith(prefix + " ")) {
                return segment.substring(prefix.length()).trim();
            }
        }
        return segment;
    }

    private static String expandState(String segment) {
        String expanded = STATES.get(segment.toLowerCase(Locale.ROOT).trim());
        return expanded == null ? segment : expanded;
    }
}
