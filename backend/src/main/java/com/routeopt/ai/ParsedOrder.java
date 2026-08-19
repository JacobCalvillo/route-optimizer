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
 * <p><strong>Why absent values are empty strings and not null.</strong> The derived schema marks
 * every component as {@code required} and typed {@code string} — Java records carry no nullability
 * information the deriver could use. Asking the model for {@code null} in the descriptions while
 * the schema forbids it puts the instructions and the grammar in direct conflict, and constrained
 * decoding loses: the model emits corrupted values like {@code ",null,"} and abandons the rest of
 * the list. So the contract asks for {@code ""}, and the compact constructor normalizes it back to
 * {@code null} the moment the object exists. Everything downstream still sees null.
 */
@JsonClassDescription("A single delivery order extracted from free-form text")
public record ParsedOrder(
        @JsonPropertyDescription(
                        "Recipient name if the text mentions one. Use an empty string when it does"
                            + " not.")
                String customerName,
        @JsonPropertyDescription(
                        "Street address exactly as written by the user, not normalized or corrected."
                            + " Use an empty string when the text contains no recognizable address.")
                String address,
        @JsonPropertyDescription(
                        "URGENT when the text expresses urgency, LOW when it says there is no rush,"
                            + " NORMAL otherwise")
                Priority priority,
        @JsonPropertyDescription(
                        "Earliest acceptable delivery time as HH:mm on a 24-hour clock. Use an empty"
                            + " string when the text does not give one.")
                String timeFrom,
        @JsonPropertyDescription(
                        "Latest acceptable delivery time as HH:mm on a 24-hour clock. Use an empty"
                            + " string when the text does not give one.")
                String timeTo,
        @JsonPropertyDescription(
                        "Any other delivery instruction worth keeping, such as an apartment number"
                            + " or a phone number. Use an empty string when there is nothing extra.")
                String notes) {

    public ParsedOrder {
        customerName = blankToNull(customerName);
        address = blankToNull(address);
        timeFrom = blankToNull(timeFrom);
        timeTo = blankToNull(timeTo);
        notes = blankToNull(notes);
        priority = priority == null ? Priority.NORMAL : priority;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
