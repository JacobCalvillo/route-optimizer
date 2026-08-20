package com.routeopt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.StructuredOutputConfig;
import com.routeopt.domain.Priority;
import org.junit.jupiter.api.Test;

/**
 * Guards the schema the SDK derives from {@link ParsedOrders}, and the contract that follows.
 *
 * <p>The derived schema is the actual prompt contract, so a change to the records silently changes
 * what the model is asked for. Two properties are load-bearing. Every field comes out
 * {@code required} and typed {@code string}, with no null allowed — which is why the descriptions
 * ask for empty strings; asking for null instead puts the instructions and the grammar in conflict,
 * and the model responds with corrupted values and a truncated list. And the address arrives in
 * parts, because the structured geocoder finds addresses its free-form search cannot.
 */
class ParsedOrderSchemaTest {

    private static String derivedSchema() {
        return StructuredOutputConfig.<ParsedOrders>builder()
                .format(ParsedOrders.class)
                .build()
                .rawOutputConfig()
                .toString();
    }

    @Test
    void derivesAnArrayOfOrdersRatherThanASingleOrder() {
        assertThat(derivedSchema()).contains("orders=", "type=array", "items=");
    }

    @Test
    void asksForTheAddressInParts() {
        // One string would defeat the structured geocoding this exists to feed.
        assertThat(derivedSchema())
                .contains("street=", "exteriorNumber=", "neighborhood=", "postalCode=", "city=", "state=");
    }

    @Test
    void keepsTheInteriorNumberSeparateFromTheStreet() {
        // An apartment number helps a driver and defeats a geocoder, so it needs its own field.
        assertThat(derivedSchema()).contains("interiorNumber=");
    }

    @Test
    void constrainsPriorityToTheEnumValues() {
        // The model cannot invent a priority: the API rejects it before it reaches our code.
        assertThat(derivedSchema()).contains("enum=[URGENT, NORMAL, LOW]");
    }

    @Test
    void marksEveryFieldRequiredAndNonNullable() {
        String schema = derivedSchema();
        assertThat(schema).contains("required=[");
        // No nullable union anywhere: this is what forces the empty-string contract.
        assertThat(schema).doesNotContain("null");
    }

    @Test
    void rejectsUnknownFields() {
        assertThat(derivedSchema()).contains("additionalProperties=false");
    }

    @Test
    void normalizesTheEmptyStringContractBackToNull() {
        ParsedOrder parsed = new ParsedOrder(
                "", "Reforma", "222", "", "Juarez", "", "Ciudad de Mexico", "",
                Priority.URGENT, "", "  ", "", "");

        assertThat(parsed.customerName()).isNull();
        assertThat(parsed.interiorNumber()).isNull();
        assertThat(parsed.postalCode()).isNull();
        assertThat(parsed.state()).isNull();
        assertThat(parsed.timeFrom()).isNull();
        assertThat(parsed.timeTo()).isNull();
        assertThat(parsed.phone()).isNull();
        assertThat(parsed.references()).isNull();
        assertThat(parsed.street()).isEqualTo("Reforma");
        assertThat(parsed.city()).isEqualTo("Ciudad de Mexico");
    }

    @Test
    void trimsSurroundingWhitespaceFromValuesTheModelReturns() {
        ParsedOrder parsed = new ParsedOrder(
                "  Juan  ", "  Reforma ", " 222 ", " 4B ", " Juarez ", " 06600 ",
                " Ciudad de Mexico ", " CDMX ", null, "09:00", "13:00", " 5512345678 ",
                " dejar con el portero ");

        assertThat(parsed.customerName()).isEqualTo("Juan");
        assertThat(parsed.street()).isEqualTo("Reforma");
        assertThat(parsed.exteriorNumber()).isEqualTo("222");
        assertThat(parsed.interiorNumber()).isEqualTo("4B");
        assertThat(parsed.phone()).isEqualTo("5512345678");
        assertThat(parsed.references()).isEqualTo("dejar con el portero");
        assertThat(parsed.priority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    void reportsWhenNothingGeocodableWasFound() {
        ParsedOrder nothing = new ParsedOrder(
                "Juan", "", "", "", "", "", "", "", Priority.NORMAL, "", "", "", "recoger algo");
        ParsedOrder streetOnly = new ParsedOrder(
                "", "Reforma", "222", "", "", "", "", "", Priority.NORMAL, "", "", "", "");

        assertThat(nothing.hasNoAddress()).isTrue();
        assertThat(streetOnly.hasNoAddress()).isFalse();
    }
}
