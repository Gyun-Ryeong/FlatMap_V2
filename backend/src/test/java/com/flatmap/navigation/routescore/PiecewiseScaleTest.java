package com.flatmap.navigation.routescore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PiecewiseScaleTest {

    // 꺾은점: 0→0, 2→20, 5→40, 10→60
    private final PiecewiseScale scale = PiecewiseScale.parse("0:0, 2:20, 5:40, 10:60");

    @Test
    void breakpointsReturnExactlyTheirValue() {
        assertThat(scale.apply(0)).isEqualTo(0);
        assertThat(scale.apply(2)).isEqualTo(20);
        assertThat(scale.apply(5)).isEqualTo(40);
        assertThat(scale.apply(10)).isEqualTo(60);
    }

    @Test
    void interpolatesLinearlyBetweenBreakpoints() {
        assertThat(scale.apply(1)).isCloseTo(10, within(1e-9));    // 0~2 구간: 기울기 10
        assertThat(scale.apply(3.5)).isCloseTo(30, within(1e-9));  // 2~5 구간: 20 + 1.5 * (20/3)
        assertThat(scale.apply(7.5)).isCloseTo(50, within(1e-9));  // 5~10 구간: 40 + 2.5 * 4
    }

    @Test
    void justAroundABoundaryChangesSlopeContinuously() {
        assertThat(scale.apply(1.999)).isLessThan(scale.apply(2));
        assertThat(scale.apply(2.001)).isGreaterThan(scale.apply(2));
        assertThat(scale.apply(2.001) - scale.apply(2)).isCloseTo(0.001 * 20.0 / 3.0, within(1e-9));
    }

    @Test
    void clampsOutsideTheEnds() {
        assertThat(scale.apply(-5)).isEqualTo(0);
        assertThat(scale.apply(10.0001)).isEqualTo(60);
        assertThat(scale.apply(1e9)).isEqualTo(60);
        assertThat(scale.apply(Double.NaN)).isEqualTo(0);
    }

    @Test
    void rejectsMalformedOrInvalidSpecs() {
        assertThatThrownBy(() -> PiecewiseScale.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:0,abc:5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:0:1,2:5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("5:10,2:20")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:0,0:10")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:0,5:101")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiecewiseScale.parse("0:-1,5:50")).isInstanceOf(IllegalArgumentException.class);
    }
}
