package com.flatmap.navigation.walk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 합성 DEM(평면)으로 Horn 경사 계산·정렬·가장자리 처리를 확인한다. 경사 단위는 각도(°). */
class SlopeGridBuilderTest {

    private static final double PX = 1.0 / 3600;
    private static final double MIN_LAT = 37.40, MIN_LNG = 127.10;

    /** 7x7 셀 격자(중심이 MIN + i/3600). */
    private static final WalkGrid GRID = new WalkGrid(MIN_LAT, MIN_LAT + 6 * PX, MIN_LNG, MIN_LNG + 6 * PX, 3600);

    /**
     * 격자 둘레로 2픽셀씩 더 덮는 11x11 DEM. 픽셀 (col,row) 의 중심 위치는 (MIN_LNG + (col-2)/3600, MIN_LAT + (8-row)/3600)
     * 이고 모서리 원점은 첫 픽셀 중심에서 반 픽셀 바깥이다(Copernicus 와 같은 배치).
     * 고도는 {@code z(eastMeters, northMeters)} 로 정한다(남서쪽 첫 픽셀 중심 기준 미터).
     */
    private static DemRaster dem(BiFunction<Double, Double, Double> z) {
        int n = 11;
        float[] v = new float[n * n];
        double dx = PX * SlopeGridBuilder.metersPerDegreeLng(MIN_LAT);
        double dy = PX * SlopeGridBuilder.metersPerDegreeLat(MIN_LAT);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                v[row * n + col] = (float) (double) z.apply(col * dx, (n - 1 - row) * dy);
            }
        }
        return new DemRaster(n, n, MIN_LNG - 2 * PX - PX / 2, MIN_LAT + 8 * PX + PX / 2, PX, PX, v);
    }

    private static Map<Integer, Double> bySlope(SlopeCells cells) {
        Map<Integer, Double> m = new HashMap<>();
        for (int k = 0; k < cells.size(); k++) m.put(cells.cellI(k) * 100 + cells.cellJ(k), cells.slopeDeg(k));
        return m;
    }

    @Test
    void flatTerrainIsZeroDegrees() {
        SlopeCells cells = SlopeGridBuilder.build(dem((e, n) -> 120.0), GRID);
        assertThat(cells.size()).isEqualTo(49);
        for (int k = 0; k < cells.size(); k++) {
            assertThat(cells.slopeDeg(k)).isZero();
            assertThat(cells.elevationM(k)).isEqualTo(120f);
        }
    }

    @Test
    void planeRisingEastHasTheSameSlopeEverywhere() {
        double tan = Math.tan(Math.toRadians(5.0));
        SlopeCells cells = SlopeGridBuilder.build(dem((e, n) -> 100 + tan * e), GRID);
        assertThat(cells.size()).isEqualTo(49);
        for (int k = 0; k < cells.size(); k++) {
            assertThat(cells.slopeDeg(k)).isCloseTo(5.0, within(0.01));
        }
    }

    @Test
    void planeRisingNorthHasTheSameSlopeAndSignDoesNotMatter() {
        double tan = Math.tan(Math.toRadians(8.0));
        SlopeCells north = SlopeGridBuilder.build(dem((e, n) -> 100 + tan * n), GRID);
        SlopeCells south = SlopeGridBuilder.build(dem((e, n) -> 100 - tan * n), GRID);
        for (int k = 0; k < north.size(); k++) {
            assertThat(north.slopeDeg(k)).isCloseTo(8.0, within(0.01));
            assertThat(south.slopeDeg(k)).isCloseTo(8.0, within(0.01));
        }
    }

    @Test
    void diagonalPlaneCombinesBothDirections() {
        double te = Math.tan(Math.toRadians(3.0)), tn = Math.tan(Math.toRadians(4.0));
        double expected = Math.toDegrees(Math.atan(Math.hypot(te, tn)));
        SlopeCells cells = SlopeGridBuilder.build(dem((e, n) -> 100 + te * e + tn * n), GRID);
        for (int k = 0; k < cells.size(); k++) {
            assertThat(cells.slopeDeg(k)).isCloseTo(expected, within(0.01));
        }
    }

    @Test
    void steeperTerrainNeverGivesALowerSlope() {
        double previous = -1;
        for (double deg : new double[]{0, 1, 2.5, 3.18, 4.76, 7.13, 15, 30, 60}) {
            double tan = Math.tan(Math.toRadians(deg));
            double slope = SlopeGridBuilder.build(dem((e, n) -> tan * e), GRID).slopeDeg(0);
            assertThat(slope).isGreaterThanOrEqualTo(previous);
            previous = slope;
        }
    }

    @Test
    void cellsAreIndexedByTheirCentreNotTheirCorner() {
        // 셀 (i, j) 는 중심이 (MIN_LNG + i/3600, MIN_LAT + j/3600) 인 픽셀의 값이다. 동쪽으로 갈수록 가파른 지형으로 구분한다.
        DemRaster d = dem((e, n) -> 0.0005 * e * e);        // 경사는 동쪽(e)이 클수록 커진다
        SlopeCells cells = SlopeGridBuilder.build(d, GRID);
        Map<Integer, Double> m = bySlope(cells);
        assertThat(m.get(0 * 100 + 3)).isLessThan(m.get(6 * 100 + 3));
        assertThat(m.get(2 * 100 + 0)).isCloseTo(m.get(2 * 100 + 6), within(1e-3));   // 남북으로는 (위도에 따른 미터 환산 차이를 빼면) 같다
        // 셀 (3, 3) 의 중심 픽셀은 DEM 의 (col 5, row 5)
        assertThat(cells.elevationM(indexOf(cells, 3, 3))).isEqualTo(d.value(5, 5));
    }

    private static int indexOf(SlopeCells cells, int i, int j) {
        for (int k = 0; k < cells.size(); k++) if (cells.cellI(k) == i && cells.cellJ(k) == j) return k;
        throw new AssertionError("셀 없음 " + i + "," + j);
    }

    @Test
    void aMissingValueDropsOnlyTheCellsWhoseNeighbourhoodTouchesIt() {
        DemRaster d = dem((e, n) -> 100.0);
        d.values()[5 * 11 + 5] = Float.NaN;          // 중심 픽셀 → 셀 (3,3) 을 중심으로 3x3 셀이 영향
        SlopeCells cells = SlopeGridBuilder.build(d, GRID);
        assertThat(cells.size()).isEqualTo(49 - 9);
        Map<Integer, Double> m = bySlope(cells);
        assertThat(m).doesNotContainKeys(3 * 100 + 3, 2 * 100 + 2, 4 * 100 + 4).containsKeys(0, 6 * 100 + 6);
    }

    @Test
    void cellsAtTheRasterEdgeAreSkippedBecauseTheyHaveNoFullNeighbourhood() {
        // 격자를 DEM 보다 넓게 잡으면(격자가 래스터 가장자리 1픽셀까지) 가장자리 셀은 만들지 않는다
        WalkGrid wide = new WalkGrid(MIN_LAT - 2 * PX, MIN_LAT + 8 * PX, MIN_LNG - 2 * PX, MIN_LNG + 8 * PX, 3600);
        SlopeCells cells = SlopeGridBuilder.build(dem((e, n) -> 100.0), wide);
        assertThat(wide.cols() * wide.rows()).isEqualTo(121);
        assertThat(cells.size()).isEqualTo(9 * 9);       // 11x11 에서 가장자리 한 겹을 뺀 9x9
        Map<Integer, Double> m = bySlope(cells);
        assertThat(m).doesNotContainKey(0).doesNotContainKey(10 * 100 + 10);
    }

    @Test
    void rejectsADemThatDoesNotLineUpWithTheGrid() {
        // 반 픽셀의 3/10 만큼 어긋난 DEM
        DemRaster shifted = new DemRaster(11, 11, MIN_LNG - 2 * PX - PX / 2 + 0.3 * PX, MIN_LAT + 8 * PX + PX / 2,
                PX, PX, new float[121]);
        assertThatThrownBy(() -> SlopeGridBuilder.build(shifted, GRID)).isInstanceOf(DemException.class);
        // 해상도가 다른 DEM
        DemRaster coarse = new DemRaster(11, 11, MIN_LNG - 2 * PX - PX, MIN_LAT + 8 * PX + PX,
                2 * PX, 2 * PX, new float[121]);
        assertThatThrownBy(() -> SlopeGridBuilder.build(coarse, GRID)).isInstanceOf(DemException.class);
    }

    @Test
    void degreeToMetreConversionMatchesWgs84AtSeongnamLatitude() {
        assertThat(SlopeGridBuilder.metersPerDegreeLat(37.4)).isCloseTo(110_985, within(10.0));
        assertThat(SlopeGridBuilder.metersPerDegreeLng(37.4)).isCloseTo(88_550, within(30.0));
    }

    @Test
    void gridSpecValidation() {
        assertThat(GRID.cols()).isEqualTo(7);
        assertThat(GRID.rows()).isEqualTo(7);
        assertThat(GRID.centerLng(3)).isCloseTo(MIN_LNG + 3 * PX, within(1e-12));
        assertThatThrownBy(() -> new WalkGrid(37.4, 37.4, 127.1, 127.2, 3600)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkGrid(37.4, 37.4005, 127.1, 127.2, 3600))      // 셀 크기의 정수 배가 아님
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkGrid(37.4, 37.5, 127.1, 127.2, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
