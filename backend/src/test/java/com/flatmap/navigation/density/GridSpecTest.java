package com.flatmap.navigation.density;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GridSpecTest {

    // application.properties 와 같은 값: 성남 범위, 경도 0.0034° x 위도 0.0027°
    private final GridSpec spec = GridSpec.covering(
            new BigDecimal("37.33"), new BigDecimal("37.53"), new BigDecimal("127.04"), new BigDecimal("127.20"),
            new BigDecimal("0.0027"), new BigDecimal("0.0034"));

    @Test
    void coversTheRangeWithTheMinimumNumberOfCells() {
        // 경도 0.16 / 0.0034 = 47.06 → 48열, 위도 0.20 / 0.0027 = 74.07 → 75행
        assertThat(spec.cols()).isEqualTo(48);
        assertThat(spec.rows()).isEqualTo(75);
    }

    @Test
    void cellIndexMapsToPolygonBoundsWithoutAccumulatedError() {
        double[] origin = spec.cellBounds(0, 0);
        assertThat(origin).containsExactly(127.04, 37.33, 127.0434, 37.3327);

        // 마지막 열·행도 min + i * cell 로 곧바로 계산된다(누적하지 않는다).
        double[] last = spec.cellBounds(47, 74);
        assertThat(last[0]).isEqualTo(127.1998);   // 127.04 + 47 * 0.0034
        assertThat(last[1]).isEqualTo(37.5298);    // 37.33 + 74 * 0.0027
        assertThat(last[2]).isEqualTo(127.2032);
        assertThat(last[3]).isEqualTo(37.5325);
    }

    @Test
    void adjacentCellsShareExactlyTheSameEdge() {
        assertThat(spec.cellBounds(10, 5)[2]).isEqualTo(spec.cellBounds(11, 5)[0]);
        assertThat(spec.cellBounds(10, 5)[3]).isEqualTo(spec.cellBounds(10, 6)[1]);
    }

    @Test
    void pointToIndexIsHalfOpenSoABoundaryBelongsToOneCell() {
        assertThat(spec.columnOf(127.04)).isEqualTo(0);
        assertThat(spec.columnOf(127.0433999)).isEqualTo(0);
        assertThat(spec.columnOf(127.0434)).isEqualTo(1);       // 경계는 위쪽 셀
        assertThat(spec.rowOf(37.3327)).isEqualTo(1);
        assertThat(spec.rowOf(37.3326999)).isEqualTo(0);
    }

    @Test
    void outsideTheGridGivesOutOfRangeIndexes() {
        assertThat(spec.contains(spec.columnOf(127.0399), spec.rowOf(37.40))).isFalse();
        assertThat(spec.contains(spec.columnOf(127.10), spec.rowOf(37.5400))).isFalse();
        assertThat(spec.contains(spec.columnOf(127.10), spec.rowOf(37.40))).isTrue();
    }

    @Test
    void centerOfACellMapsBackToItsIndex() {
        for (int[] ij : new int[][]{{0, 0}, {13, 40}, {47, 74}}) {
            double[] b = spec.cellBounds(ij[0], ij[1]);
            assertThat(spec.columnOf((b[0] + b[2]) / 2)).isEqualTo(ij[0]);
            assertThat(spec.rowOf((b[1] + b[3]) / 2)).isEqualTo(ij[1]);
        }
    }

    @Test
    void rejectsEmptyRangesAndNonPositiveCells() {
        assertThatThrownBy(() -> GridSpec.covering(new BigDecimal("37.5"), new BigDecimal("37.4"),
                new BigDecimal("127.0"), new BigDecimal("127.1"), new BigDecimal("0.0027"), new BigDecimal("0.0034")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GridSpec(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
