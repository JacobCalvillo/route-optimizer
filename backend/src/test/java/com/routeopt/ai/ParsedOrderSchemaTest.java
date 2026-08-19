package com.routeopt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.StructuredOutputConfig;
import com.routeopt.domain.Priority;
import org.junit.jupiter.api.Test;

/**
 * Guards the schema the SDK derives from {@link ParsedOrders}, and the contract that follows from
 * it.
 *
 * <p>The derived schema is the actual prompt contract, so a change to the records silently changes
 * what the model is asked for. One property in particular is load-bearing: every field comes out
 * {@code required} and typed {@code string}, with no null allowed. That is why the descriptions ask
 * for empty strings — asking for null instead puts the instructions and the grammar in conflict,
 * and the model responds with corrupted values and a truncated list.
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
    void constrainsPriorityToTheEnumValues() {
        // The model cannot invent a priority: the API rejects it before it reaches our code.
        assertThat(derivedSchema()).contains("enum=[URGENT, NORMAL, LOW]");
    }

    @Test
    void marksEveryFieldRequiredAndNonNullable() {
        String schema = derivedSchema();
        assertThat(schema).contains("required=[address, customerName, notes, priority, timeFrom, timeTo]");
        // No nullable union anywhere: this is what forces the empty-string contract.
        assertThat(schema).doesNotContain("null");
    }

    @Test
    void rejectsUnknownFields() {
        assertThat(derivedSchema()).contains("additionalProperties=false");
    }

    @Test
    void normalizesTheEmptyStringContractBackToNull() {
        ParsedOrder parsed = new ParsedOrder("", "Reforma 222", Priority.URGENT, "", "  ", "");

        assertThat(parsed.customerName()).isNull();
        assertThat(parsed.timeFrom()).isNull();
        assertThat(parsed.timeTo()).isNull();
        assertThat(parsed.notes()).isNull();
        assertThat(parsed.address()).isEqualTo("Reforma 222");
    }

    @Test
    void trimsSurroundingWhitespaceFromValuesTheModelReturns() {
        ParsedOrder parsed =
                new ParsedOrder("  Juan  ", "  Reforma 222 ", null, "09:00", "13:00", " depto 4B ");

        assertThat(parsed.customerName()).isEqualTo("Juan");
        assertThat(parsed.address()).isEqualTo("Reforma 222");
        assertThat(parsed.notes()).isEqualTo("depto 4B");
        assertThat(parsed.priority()).isEqualTo(Priority.NORMAL);
    }
}
