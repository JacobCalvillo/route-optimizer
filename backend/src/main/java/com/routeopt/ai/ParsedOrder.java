package com.routeopt.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.routeopt.domain.Priority;

/**
 * One delivery order as extracted from free-form text.
 *
 * <p>The Anthropic SDK derives the JSON schema sent to the model from this record, so the
 * descriptions below are part of the prompt, not just documentation.
 */
@JsonClassDescription("A single delivery order extracted from free-form text")
public record ParsedOrder(
        @JsonPropertyDescription("Recipient name if the text mentions one, otherwise null")
                String customerName,
        @JsonPropertyDescription(
                        "Street address exactly as written by the user, not normalized or corrected."
                            + " Null when the text contains no recognizable address.")
                String address,
        @JsonPropertyDescription(
                        "URGENT when the text expresses urgency, LOW when it says there is no rush,"
                            + " NORMAL otherwise")
                Priority priority,
        @JsonPropertyDescription(
                        "Earliest acceptable delivery time as HH:mm on a 24-hour clock, or null when"
                            + " the text does not give one")
                String timeFrom,
        @JsonPropertyDescription(
                        "Latest acceptable delivery time as HH:mm on a 24-hour clock, or null when"
                            + " the text does not give one")
                String timeTo,
        @JsonPropertyDescription(
                        "Any other delivery instruction worth keeping, such as an apartment number"
                            + " or a phone number. Null when there is nothing extra.")
                String notes) {}
