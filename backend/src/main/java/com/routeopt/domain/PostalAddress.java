package com.routeopt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;

/**
 * A Mexican address broken into the parts a geocoder can use.
 *
 * <p>Keeping these apart rather than in one string is not tidiness — it is what makes geocoding
 * work. Nominatim's structured query resolves addresses its free-form search cannot: a Nuevo León
 * delivery that returned nothing as a sentence was found on the first attempt from
 * {@code street + city + state}, even with the wrong postal code attached. And an ambiguous
 * "Paseo de la Reforma 222" that free-form put in Quintana Roo lands in Mexico City once the city
 * is its own field.
 *
 * <p>{@code interiorNumber} is deliberately part of the address but never part of the query. An
 * apartment number tells a driver where to knock and tells a geocoder nothing; leaving it in the
 * search string is one of the reasons real addresses fail to resolve.
 *
 * <p>{@code neighborhood} (colonia or fraccionamiento) has no structured parameter in Nominatim, so
 * it is kept for display and for the free-form fallback, where it does help.
 */
@Embeddable
public class PostalAddress {

    @Column(length = 300)
    private String street;

    @Column(length = 40)
    private String exteriorNumber;

    /** Apartment, suite or floor. Shown to the driver, never sent to the geocoder. */
    @Column(length = 40)
    private String interiorNumber;

    /** Colonia or fraccionamiento. No structured parameter exists, so it only aids the fallback. */
    @Column(length = 200)
    private String neighborhood;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 160)
    private String city;

    @Column(length = 160)
    private String state;

    public PostalAddress() {}

    public PostalAddress(
            String street,
            String exteriorNumber,
            String interiorNumber,
            String neighborhood,
            String postalCode,
            String city,
            String state) {
        this.street = trimToNull(street);
        this.exteriorNumber = trimToNull(exteriorNumber);
        this.interiorNumber = trimToNull(interiorNumber);
        this.neighborhood = trimToNull(neighborhood);
        this.postalCode = trimToNull(postalCode);
        this.city = trimToNull(city);
        this.state = trimToNull(state);
    }

    /** Whether there is enough here to attempt a structured query at all. */
    public boolean isGeocodable() {
        return street != null || postalCode != null || city != null;
    }

    public boolean isEmpty() {
        return street == null
                && exteriorNumber == null
                && interiorNumber == null
                && neighborhood == null
                && postalCode == null
                && city == null
                && state == null;
    }

    /**
     * The {@code street} parameter: street name and exterior number, in that order.
     *
     * <p>The interior number is excluded on purpose — see the class comment.
     */
    public String streetLine() {
        if (street == null) {
            return null;
        }
        return exteriorNumber == null ? street : street + " " + exteriorNumber;
    }

    /** Everything as one line, for display and for the free-form fallback. */
    public String toSingleLine() {
        List<String> parts = new ArrayList<>();
        if (streetLine() != null) {
            parts.add(streetLine());
        }
        addIfPresent(parts, neighborhood);
        addIfPresent(parts, postalCode);
        addIfPresent(parts, city);
        addIfPresent(parts, state);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = trimToNull(street);
    }

    public String getExteriorNumber() {
        return exteriorNumber;
    }

    public void setExteriorNumber(String exteriorNumber) {
        this.exteriorNumber = trimToNull(exteriorNumber);
    }

    public String getInteriorNumber() {
        return interiorNumber;
    }

    public void setInteriorNumber(String interiorNumber) {
        this.interiorNumber = trimToNull(interiorNumber);
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = trimToNull(neighborhood);
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = trimToNull(postalCode);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = trimToNull(city);
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = trimToNull(state);
    }
}
