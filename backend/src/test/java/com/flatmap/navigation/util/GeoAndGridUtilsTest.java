package com.flatmap.navigation.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 거리(haversine)와 기상청 격자 변환. 기대값은 공개된 지리 값(도시 간 거리, 기상청 격자표)이다. */
class GeoAndGridUtilsTest {

    // ---- GeoUtils ----

    @Test
    void sameOrSymmetricPointsGiveZeroOrEqualDistance() {
        assertThat(GeoUtils.haversineMeters(37.4, 127.1, 37.4, 127.1)).isZero();
        double ab = GeoUtils.haversineMeters(37.40, 127.10, 37.45, 127.15);
        double ba = GeoUtils.haversineMeters(37.45, 127.15, 37.40, 127.10);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        // 반지름 6,371km 구 기준 1° = 111,194.9m
        assertThat(GeoUtils.haversineMeters(37.0, 127.0, 38.0, 127.0)).isCloseTo(111_195, within(5.0));
    }

    @Test
    void longitudeDegreeShrinksWithLatitude() {
        double atEquator = GeoUtils.haversineMeters(0, 127.0, 0, 128.0);
        double atSeongnam = GeoUtils.haversineMeters(37.4, 127.0, 37.4, 128.0);
        assertThat(atEquator).isCloseTo(111_195, within(5.0));
        assertThat(atSeongnam).isCloseTo(111_195 * Math.cos(Math.toRadians(37.4)), within(30.0));
    }

    @Test
    void seoulToBusanIsAbout325Km() {
        double km = GeoUtils.haversineMeters(37.5665, 126.9780, 35.1796, 129.0756) / 1000;
        assertThat(km).isCloseTo(325, within(3.0));
    }

    // ---- GridConverter (기상청 격자표 값) ----

    @Test
    void knownCitiesMapToTheirPublishedKmaGridCells() {
        assertThat(GridConverter.toGrid(37.5665, 126.9780)).containsExactly(60, 127);   // 서울
        assertThat(GridConverter.toGrid(35.1796, 129.0756)).containsExactly(98, 76);    // 부산
        assertThat(GridConverter.toGrid(36.3504, 127.3845)).containsExactly(67, 100);   // 대전
    }

    @Test
    void gridIndexGrowsEastwardAndNorthward() {
        int[] base = GridConverter.toGrid(37.40, 127.10);
        assertThat(GridConverter.toGrid(37.40, 127.20)[0]).isGreaterThan(base[0]);
        assertThat(GridConverter.toGrid(37.50, 127.10)[1]).isGreaterThan(base[1]);
        // 5km 격자: 위도 0.1°(약 11km)는 두 칸쯤
        assertThat(GridConverter.toGrid(37.50, 127.10)[1] - base[1]).isBetween(2, 3);
    }

    @Test
    void pointsInsideOneFiveKmCellShareTheGrid() {
        // 같은 격자 안의 두 지점은 같은 격자 번호 → 날씨 캐시가 한 번의 외부 호출을 공유한다
        int[] a = GridConverter.toGrid(37.4200, 127.1265);
        int[] b = GridConverter.toGrid(37.4210, 127.1275);
        assertThat(a).containsExactly(b);
    }
}
