package com.flatmap.navigation.routescore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteGeometryTest {

    @Test
    void wktIsLngLatOrderAndOneLinestring() {
        String wkt = RouteGeometry.toWkt(List.of(new double[]{37.4, 127.1}, new double[]{37.41, 127.11}));
        assertThat(wkt).isEqualTo("LINESTRING(127.10000000 37.40000000, 127.11000000 37.41000000)");
    }

    @Test
    void singlePointBecomesPoint() {
        assertThat(RouteGeometry.toWkt(List.<double[]>of(new double[]{37.4, 127.1})))
                .isEqualTo("POINT(127.10000000 37.40000000)");
    }

    @Test
    void neverUsesScientificNotation() {
        String wkt = RouteGeometry.toWkt(List.of(new double[]{1.0E-5, 127.1}, new double[]{37.4, 127.1}));
        assertThat(wkt).doesNotContainPattern("\\d[eE][-+]?\\d");
    }

    @Test
    void rejectsEmptyAndNonFiniteInput() {
        assertThatThrownBy(() -> RouteGeometry.toWkt(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteGeometry.toWkt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteGeometry.toWkt(List.of(new double[]{Double.NaN, 127.1})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
