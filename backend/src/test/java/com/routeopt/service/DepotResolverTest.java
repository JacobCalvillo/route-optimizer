package com.routeopt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeopt.domain.Coordinate;
import com.routeopt.geo.GeocodeResult;
import com.routeopt.geo.GeocodingService;
import com.routeopt.service.DepotResolver.ResolvedDepot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepotResolverTest {

    private static final Coordinate ZOCALO = new Coordinate(19.4326, -99.1332);

    /** Records what it was asked, so the tests can assert the geocoder was skipped when it should be. */
    private static final class RecordingGeocoder implements GeocodingService {
        private final GeocodeResult answer;
        private final List<String> asked = new ArrayList<>();

        RecordingGeocoder(GeocodeResult answer) {
            this.answer = answer;
        }

        @Override
        public GeocodeResult geocode(String address) {
            asked.add(address);
            return answer;
        }
    }

    private static RecordingGeocoder resolvesTo(Coordinate coordinate, String displayName) {
        return new RecordingGeocoder(GeocodeResult.found(coordinate, displayName, false));
    }

    private static RecordingGeocoder findsNothing() {
        return new RecordingGeocoder(GeocodeResult.notFound(false));
    }

    @Test
    void geocodesAPlainAddress() {
        RecordingGeocoder geocoder = resolvesTo(ZOCALO, "Plaza de la Constitución, Ciudad de México");

        ResolvedDepot depot =
                new DepotResolver(geocoder).resolve("Zocalo, Ciudad de Mexico", null, null, null);

        assertThat(depot.coordinate()).isEqualTo(ZOCALO);
        assertThat(geocoder.asked).containsExactly("Zocalo, Ciudad de Mexico");
    }

    @Test
    void namesTheDepotAfterWhereItActuallyResolved() {
        // So the map tooltip says where it landed, not just what was typed.
        RecordingGeocoder geocoder = resolvesTo(ZOCALO, "Plaza de la Constitución, Ciudad de México");

        ResolvedDepot depot = new DepotResolver(geocoder).resolve("Zocalo", null, null, null);

        assertThat(depot.label()).isEqualTo("Plaza de la Constitución, Ciudad de México");
    }

    @Test
    void keepsAnExplicitLabelOverTheGeocodedName() {
        RecordingGeocoder geocoder = resolvesTo(ZOCALO, "Plaza de la Constitución");

        ResolvedDepot depot =
                new DepotResolver(geocoder).resolve("Zocalo", null, null, "Central Warehouse");

        assertThat(depot.label()).isEqualTo("Central Warehouse");
    }

    @Test
    void acceptsAPastedCoordinatePairWithoutGeocoding() {
        RecordingGeocoder geocoder = findsNothing();

        ResolvedDepot depot =
                new DepotResolver(geocoder).resolve("19.4326, -99.1332", null, null, null);

        assertThat(depot.coordinate()).isEqualTo(ZOCALO);
        assertThat(geocoder.asked).isEmpty();
    }

    @Test
    void acceptsACoordinatePairWithUntidySpacing() {
        RecordingGeocoder geocoder = findsNothing();
        DepotResolver resolver = new DepotResolver(geocoder);

        assertThat(resolver.resolve("  19.4326 ,-99.1332  ", null, null, null).coordinate())
                .isEqualTo(ZOCALO);
        assertThat(geocoder.asked).isEmpty();
    }

    @Test
    void prefersExplicitCoordinatesOverAnAddress() {
        RecordingGeocoder geocoder = resolvesTo(new Coordinate(0, 0), "somewhere else");

        ResolvedDepot depot =
                new DepotResolver(geocoder).resolve("Zocalo", 19.4326, -99.1332, null);

        assertThat(depot.coordinate()).isEqualTo(ZOCALO);
        assertThat(geocoder.asked).isEmpty();
    }

    @Test
    void doesNotMistakeAStreetNumberForCoordinates() {
        // "Reforma 222" must go to the geocoder; only a bare numeric pair short-circuits.
        RecordingGeocoder geocoder = resolvesTo(ZOCALO, "Reforma 222");
        DepotResolver resolver = new DepotResolver(geocoder);

        resolver.resolve("Reforma 222", null, null, null);
        resolver.resolve("Calle 5, 20", null, null, null);

        assertThat(geocoder.asked).containsExactly("Reforma 222", "Calle 5, 20");
    }

    @Test
    void failsWithAnActionableMessageWhenTheAddressCannotBeFound() {
        assertThatThrownBy(() ->
                        new DepotResolver(findsNothing()).resolve("zzqq no existe", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zzqq no existe")
                .hasMessageContaining("Add the city");
    }

    @Test
    void failsWhenNothingUsableWasSupplied() {
        DepotResolver resolver = new DepotResolver(findsNothing());

        assertThatThrownBy(() -> resolver.resolve(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either an address or both lat and lon");
        assertThatThrownBy(() -> resolver.resolve("   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A half-supplied pair is not usable either.
        assertThatThrownBy(() -> resolver.resolve(null, 19.4326, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCoordinatesOutsideTheValidRange() {
        assertThatThrownBy(() ->
                        new DepotResolver(findsNothing()).resolve("95.0, -99.1332", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }
}
