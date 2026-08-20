package com.routeopt.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddressQueriesTest {

    /** The address that started this: Nominatim finds nothing, yet the street is in OpenStreetMap. */
    private static final String REAL_FAILURE =
            "Fracc. Felipe Tena Ramirez 101, Praderas de Huinala, 66642 Loma La paz, N.L.";

    @Test
    void reachesTheQueryThatActuallyResolvesTheRealFailure() {
        // Verified against live Nominatim: this exact string returns the street, the full one
        // returns nothing, and neither removing the prefix nor trimming the tail works alone.
        assertThat(AddressQueries.ladder(REAL_FAILURE))
                .contains("Felipe Tena Ramirez 101, Praderas de Huinala");
    }

    @Test
    void triesTheAddressAsWrittenFirst() {
        // The first rung is the only one that can count as an exact match, so it must be untouched.
        assertThat(AddressQueries.ladder(REAL_FAILURE).getFirst()).isEqualTo(REAL_FAILURE);
    }

    @Test
    void getsShorterWithEveryRung() {
        List<String> ladder = AddressQueries.ladder(REAL_FAILURE);

        assertThat(ladder).hasSizeGreaterThan(2);
        for (int i = 1; i < ladder.size(); i++) {
            assertThat(ladder.get(i).length()).isLessThan(ladder.get(i - 1).length());
        }
    }

    @Test
    void stripsPostalCodesAndPlaceTypePrefixes() {
        List<String> ladder = AddressQueries.ladder(REAL_FAILURE);

        assertThat(ladder.get(1)).doesNotContain("66642").doesNotContain("Fracc.");
    }

    @Test
    void expandsStateAbbreviations() {
        assertThat(AddressQueries.ladder("Calle 5 100, Centro, N.L.").get(1))
                .contains("Nuevo León")
                .doesNotContain("N.L.");
        assertThat(AddressQueries.ladder("Reforma 222, Juarez, CDMX").get(1))
                .contains("Ciudad de México");
    }

    @Test
    void neverGoesBelowTwoSegments() {
        // A bare street name matches half the country, so it is not worth a request.
        assertThat(AddressQueries.ladder("Calle 5 100, Centro, Monterrey, N.L."))
                .allSatisfy(query -> assertThat(query.split(",")).hasSizeGreaterThanOrEqualTo(2));
    }

    @Test
    void leavesACleanAddressAsASingleAttempt() {
        // Nothing to strip and only two segments: no reason to spend extra requests.
        assertThat(AddressQueries.ladder("Paseo de la Reforma 222, Ciudad de Mexico"))
                .containsExactly("Paseo de la Reforma 222, Ciudad de Mexico");
    }

    @Test
    void capsTheNumberOfAttempts() {
        String long_ = "Calle 1 100, Colonia Dos, Tres, Cuatro, Cinco, Seis, Siete, Ocho, N.L.";

        assertThat(AddressQueries.ladder(long_)).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void handlesAnAddressWithNoCommas() {
        assertThat(AddressQueries.ladder("Zocalo Ciudad de Mexico"))
                .containsExactly("Zocalo Ciudad de Mexico");
    }

    @Test
    void returnsNothingForEmptyInput() {
        assertThat(AddressQueries.ladder(null)).isEmpty();
        assertThat(AddressQueries.ladder("   ")).isEmpty();
    }

    @Test
    void doesNotStripDigitsThatAreNotPostalCodes() {
        // A five-digit street number would be unusual, but 101 and 222 must survive.
        assertThat(AddressQueries.ladder("Felipe Tena Ramirez 101, Praderas de Huinala, N.L.").get(1))
                .contains("101");
    }
}
