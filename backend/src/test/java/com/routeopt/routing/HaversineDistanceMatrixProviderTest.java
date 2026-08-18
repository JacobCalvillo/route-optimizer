package com.routeopt.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeopt.config.AppProperties;
import com.routeopt.domain.Coordinate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HaversineDistanceMatrixProviderTest {

    private static final Coordinate MEXICO_CITY = new Coordinate(19.4326, -99.1332);
    private static final Coordinate GUADALAJARA = new Coordinate(20.6597, -103.3496);

    @Test
    void computesAKnownGreatCircleDistance() {
        // Mexico City to Guadalajara is about 461 km in a straight line.
        double meters = HaversineDistanceMatrixProvider.haversineMeters(MEXICO_CITY, GUADALAJARA);
        assertThat(meters).isCloseTo(461_000, org.assertj.core.data.Offset.offset(10_000.0));
    }

    @Test
    void matrixIsSymmetricWithAZeroDiagonal() {
        AppProperties properties = TestFixtures.properties(0, 0);
        DistanceMatrix matrix = TestFixtures.haversineProvider(properties)
                .compute(List.of(MEXICO_CITY, GUADALAJARA, new Coordinate(19.05, -98.20)));

        for (int i = 0; i < matrix.size(); i++) {
            assertThat(matrix.distance(i, i)).isZero();
            for (int j = 0; j < matrix.size(); j++) {
                assertThat(matrix.distance(i, j)).isEqualTo(matrix.distance(j, i));
                assertThat(matrix.duration(i, j)).isEqualTo(matrix.duration(j, i));
            }
        }
    }

    @Test
    void appliesTheDetourFactorToApproximateRoadDistance() {
        AppProperties properties = TestFixtures.properties(0, 0);
        DistanceMatrix matrix =
                TestFixtures.haversineProvider(properties).compute(List.of(MEXICO_CITY, GUADALAJARA));

        double straightLine = HaversineDistanceMatrixProvider.haversineMeters(MEXICO_CITY, GUADALAJARA);
        assertThat(matrix.distance(0, 1)).isCloseTo(straightLine * 1.3, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void derivesDurationFromTheConfiguredAverageSpeed() {
        AppProperties properties = TestFixtures.properties(0, 0);
        DistanceMatrix matrix =
                TestFixtures.haversineProvider(properties).compute(List.of(MEXICO_CITY, GUADALAJARA));

        // 30 km/h means 30000 metres per 3600 seconds.
        double expectedSeconds = matrix.distance(0, 1) / (30_000.0 / 3600.0);
        assertThat(matrix.duration(0, 1)).isCloseTo(expectedSeconds, org.assertj.core.data.Offset.offset(0.001));
    }
}
