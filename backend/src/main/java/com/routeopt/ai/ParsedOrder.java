package com.routeopt.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.routeopt.domain.Priority;

/**
 * One delivery order as extracted from free-form text.
 *
 * <p>The Anthropic SDK derives the JSON schema sent to the model from this record, so the
 * descriptions below are part of the prompt, not just documentation.
 *
 * <p>The address arrives split into parts rather than as one string, because that is what the
 * geocoder can actually use. Splitting a Mexican address is exactly the kind of thing a language
 * model is good at and a regular expression is not, and the payoff is concrete: a Nuevo León
 * address that free-form search could not find resolves immediately from
 * {@code street + city + state}.
 *
 * <p><strong>Why absent values are empty strings and not null.</strong> The derived schema marks
 * every component {@code required} and typed {@code string} — Java records carry no nullability
 * information the deriver could use. Asking the model for {@code null} while the schema forbids it
 * puts the instructions and the grammar in direct conflict, and constrained decoding wins: the
 * model emits corrupted values and abandons the rest of the list. So the contract asks for
 * {@code ""}, and the compact constructor normalizes it back to {@code null}.
 */
@JsonClassDescription("A single delivery order extracted from free-form text")
public record ParsedOrder(
        @JsonPropertyDescription("Recipient name if the text mentions one, otherwise an empty string")
                String customerName,
        @JsonPropertyDescription(
                        "Street name only, without the number, exactly as written. Empty string when"
                            + " the text names no street.")
                String street,
        @JsonPropertyDescription(
                        "Street number of the building, digits only where possible. Empty string when"
                            + " absent.")
                String exteriorNumber,
        @JsonPropertyDescription(
                        "Apartment, suite, floor or internal number - for example \"4B\", \"depto 12\","
                            + " \"piso 3\". Empty string when absent.")
                String interiorNumber,
        @JsonPropertyDescription(
                        "Colonia or fraccionamiento, without the words \"Col.\" or \"Fracc.\". Empty"
                            + " string when absent.")
                String neighborhood,
        @JsonPropertyDescription("Five-digit postal code, or an empty string when absent")
                String postalCode,
        @JsonPropertyDescription(
                        "City, municipio or delegacion. Empty string when the text does not say.")
                String city,
        @JsonPropertyDescription(
                        "Mexican state, written in full - \"Nuevo Leon\", not \"N.L.\". Empty string"
                            + " when the text does not say.")
                String state,
        @JsonPropertyDescription(
                        "URGENT when the text expresses urgency, LOW when it says there is no rush,"
                            + " NORMAL otherwise")
                Priority priority,
        @JsonPropertyDescription(
                        "Earliest acceptable delivery time as HH:mm on a 24-hour clock. Empty string"
                            + " when the text does not give one.")
                String timeFrom,
        @JsonPropertyDescription(
                        "Latest acceptable delivery time as HH:mm on a 24-hour clock. Empty string"
                            + " when the text does not give one.")
                String timeTo,
        @JsonPropertyDescription(
                        "Contact phone number for the delivery, digits and separators as written."
                            + " Empty string when absent.")
                String phone,
        @JsonPropertyDescription(
                        "Anything else that helps the driver find the door: landmarks, gate codes,"
                            + " \"between X and Y\", \"leave with the porter\". Never the address"
                            + " itself. Empty string when there is nothing.")
                String references) {

    public ParsedOrder {
        customerName = blankToNull(customerName);
        street = blankToNull(street);
        exteriorNumber = blankToNull(exteriorNumber);
        interiorNumber = blankToNull(interiorNumber);
        neighborhood = blankToNull(neighborhood);
        postalCode = blankToNull(postalCode);
        city = blankToNull(city);
        state = blankToNull(state);
        timeFrom = blankToNull(timeFrom);
        timeTo = blankToNull(timeTo);
        phone = blankToNull(phone);
        references = blankToNull(references);
        priority = priority == null ? Priority.NORMAL : priority;
    }

    /** True when the model found nothing that could be geocoded. */
    public boolean hasNoAddress() {
        return street == null && postalCode == null && city == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
