package com.routeopt.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostalAddressTest {

    /** The real Nuevo Leon address that free-form search could not find. */
    private static PostalAddress apodaca() {
        return new PostalAddress(
                "Felipe Tena Ramirez", "101", "4B", "Praderas de Huinala", "66642", "Apodaca",
                "Nuevo Leon");
    }

    @Test
    void putsTheExteriorNumberOnTheStreetLine() {
        assertThat(apodaca().streetLine()).isEqualTo("Felipe Tena Ramirez 101");
    }

    @Test
    void keepsTheInteriorNumberOutOfTheStreetLine() {
        // The one rule that matters most here: an apartment number defeats a geocoder.
        assertThat(apodaca().streetLine()).doesNotContain("4B");
    }

    @Test
    void survivesAStreetWithNoNumber() {
        PostalAddress noNumber =
                new PostalAddress("Avenida Chapultepec", null, null, null, null, "Guadalajara", null);

        assertThat(noNumber.streetLine()).isEqualTo("Avenida Chapultepec");
    }

    @Test
    void buildsASingleLineForDisplayAndForTheFreeFormFallback() {
        assertThat(apodaca().toSingleLine())
                .isEqualTo("Felipe Tena Ramirez 101, Praderas de Huinala, 66642, Apodaca, Nuevo Leon");
    }

    @Test
    void skipsMissingPartsInTheSingleLine() {
        PostalAddress sparse =
                new PostalAddress("Reforma", "222", null, null, null, "Ciudad de Mexico", null);

        assertThat(sparse.toSingleLine()).isEqualTo("Reforma 222, Ciudad de Mexico");
    }

    @Test
    void treatsBlanksAsAbsent() {
        PostalAddress blanks = new PostalAddress("  ", "", "   ", "", "", "", "");

        assertThat(blanks.isEmpty()).isTrue();
        assertThat(blanks.toSingleLine()).isNull();
        assertThat(blanks.streetLine()).isNull();
    }

    @Test
    void needsAStreetCityOrPostalCodeToBeWorthSearching() {
        assertThat(new PostalAddress(null, null, null, "Centro", null, null, "Jalisco").isGeocodable())
                .isFalse();
        assertThat(new PostalAddress("Reforma", null, null, null, null, null, null).isGeocodable())
                .isTrue();
        assertThat(new PostalAddress(null, null, null, null, "06600", null, null).isGeocodable())
                .isTrue();
        assertThat(new PostalAddress(null, null, null, null, null, "Apodaca", null).isGeocodable())
                .isTrue();
    }

    @Test
    void trimsEveryPart() {
        PostalAddress padded =
                new PostalAddress(" Reforma ", " 222 ", " 4B ", " Juarez ", " 06600 ", " CDMX ", " CDMX ");

        assertThat(padded.getStreet()).isEqualTo("Reforma");
        assertThat(padded.getExteriorNumber()).isEqualTo("222");
        assertThat(padded.getPostalCode()).isEqualTo("06600");
        assertThat(padded.getCity()).isEqualTo("CDMX");
    }
}
