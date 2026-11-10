package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 경로 반경 이내 급경사지 판별(findRiskSections)의 동작 고정 테스트.
 *
 * <p>haversine+findAll() 구현을 ST_DWithin(geography) 쿼리로 바꾸기 전에 기존 동작을 먼저 고정했고,
 * 교체 뒤에도 같은 단언이 통과해야 한다. 거리 오차(구면 vs 타원체 약 0.2%)는 허용 오차 안이며,
 * 경계 픽스처는 반경(200m)에서 5% 이상 떨어뜨려 두었다.
 *
 * <p>픽스처: 위도 37.4000 을 따라 동쪽으로 가는 경로(경도 127.1000~127.1100, 0.0005° ≈ 44m 간격, 좌표 21개).
 * 위도 0.001° ≈ 111m.
 */
@IntegrationTest
class SteepSlopeServiceTest {

    private static final double ROUTE_LAT = 37.4000;
    private static final double ROUTE_LNG0 = 127.1000;
    private static final double LNG_STEP = 0.0005;

    @Autowired
    SteepSlopeService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM steep_slope_areas");
    }

    private static List<RouteResponse.Coord> eastRoute() {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            coords.add(new RouteResponse.Coord(ROUTE_LAT, ROUTE_LNG0 + i * LNG_STEP));
        }
        return coords;
    }

    private void insertArea(String name, Double lat, Double lng, Double grade) {
        // geom 이 쓰기 원본이다. 좌표가 null 이면 geom 도 null.
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) " +
                        "VALUES (?, ?, 'TEST', ST_SetSRID(ST_MakePoint(?::float8, ?::float8), 4326))",
                name, grade, lng, lat);
    }

    @Test
    void emptyTableYieldsNoRiskSections() {
        assertThat(service.findRiskSections(eastRoute())).isEmpty();
    }

    @Test
    void emptyRouteYieldsNoRiskSections() {
        insertArea("A", 37.4010, 127.1020, 12.5);
        assertThat(service.findRiskSections(List.of())).isEmpty();
    }

    @Test
    void radiusBoundaryInsideIsMatchedAndOutsideIsNot() {
        // 경로 바로 위(경도는 정점 idx 10)에서 북쪽으로 떨어진 거리: 189m(반경 안) / 211m(반경 밖)
        insertArea("inside-189m", ROUTE_LAT + 0.0017, 127.1050, 5.0);
        insertArea("outside-211m", ROUTE_LAT + 0.0019, 127.1080, 5.0);

        List<RiskSection> result = service.findRiskSections(eastRoute());

        assertThat(result).extracting(RiskSection::getName).containsExactly("inside-189m");
        assertThat(result.get(0).getDistanceFromRoute()).isCloseTo(189.0, within(1.0));
        assertThat(result.get(0).getNearestRouteIdx()).isEqualTo(10);
    }

    @Test
    void nearestRouteIdxIsTheNearestVertexAndResultsAreOrderedAlongTheRoute() {
        // 일부러 경로 순서의 역순으로 넣는다. 결과는 nearestRouteIdx 오름차순이어야 한다.
        insertArea("F-idx18", ROUTE_LAT - 0.0010, 127.1090, 7.0);   // 정점 18, 남쪽 111m
        insertArea("B-idx10", ROUTE_LAT + 0.0017, 127.1050, 3.0);   // 정점 10, 북쪽 189m
        insertArea("A-idx4", ROUTE_LAT + 0.0010, 127.1020, 12.5);   // 정점 4, 북쪽 111m
        // 정점 2(127.1010)와 3(127.1015) 사이이되 2번에 더 가깝다: 경도 차 0.0002°≈17.7m, 북쪽 55.6m
        insertArea("G-idx2", ROUTE_LAT + 0.0005, 127.1012, 1.0);

        List<RiskSection> result = service.findRiskSections(eastRoute());

        assertThat(result).extracting(RiskSection::getName)
                .containsExactly("G-idx2", "A-idx4", "B-idx10", "F-idx18");
        assertThat(result).extracting(RiskSection::getNearestRouteIdx)
                .containsExactly(2, 4, 10, 18);
        assertThat(result.get(0).getDistanceFromRoute()).isCloseTo(58.3, within(1.0));
        assertThat(result.get(1).getDistanceFromRoute()).isCloseTo(111.0, within(1.0));
    }

    @Test
    void areaDetailsAreCarriedIntoTheSection() {
        insertArea("A", ROUTE_LAT + 0.0010, 127.1020, 12.5);

        RiskSection s = service.findRiskSections(eastRoute()).get(0);

        assertThat(s.getName()).isEqualTo("A");
        assertThat(s.getLat()).isEqualTo(ROUTE_LAT + 0.0010);
        assertThat(s.getLng()).isEqualTo(127.1020);
        assertThat(s.getGrade()).isEqualTo(12.5);   // 단위 각도(°)
    }

    @Test
    void areasWithoutCoordinatesAreIgnored() {
        insertArea("no-coords", null, null, 9.0);
        insertArea("A", ROUTE_LAT + 0.0010, 127.1020, 12.5);

        assertThat(service.findRiskSections(eastRoute())).extracting(RiskSection::getName)
                .containsExactly("A");
    }

    @Test
    void areaFarFromTheRouteIsNotMatched() {
        insertArea("far", 37.4500, 127.1050, 10.0);

        assertThat(service.findRiskSections(eastRoute())).isEmpty();
    }

    // ----- 날씨 보정: null 등급(붕괴위험 지정지) 구간 NPE 회귀 -----

    private static WeatherInfo snow() {
        WeatherInfo w = WeatherInfo.fallback();
        w.setPrecipitationType(3);   // 눈 → multiplier 1.8
        return w;
    }

    @Test
    void weatherAdjustmentDoesNotThrowForSectionsWithoutLevel() {
        List<RiskSection> sections = new ArrayList<>(List.of(
                new RiskSection(37.4, 127.1, "no-level", 10.0, null, 50, 3)));

        service.adjustRiskByWeather(sections, snow());

        assertThat(sections.get(0).getRiskLevel()).isNull();
    }

    @Test
    void weatherAdjustmentSkipsNullLevelsAndStillUpgradesOthers() {
        List<RiskSection> sections = new ArrayList<>(List.of(
                new RiskSection(37.4, 127.1, "no-level", 10.0, null, 50, 3),
                new RiskSection(37.4, 127.1, "low", 2.0, "LOW", 50, 4)));

        service.adjustRiskByWeather(sections, snow());

        assertThat(sections.get(0).getRiskLevel()).isNull();
        assertThat(sections.get(1).getRiskLevel()).isEqualTo("VERY_HIGH");
    }

    @Test
    void weatherAdjustmentIsNoopForNullWeatherOrEmptyList() {
        List<RiskSection> sections = new ArrayList<>(List.of(
                new RiskSection(37.4, 127.1, "low", 2.0, "LOW", 50, 4)));

        service.adjustRiskByWeather(sections, null);
        service.adjustRiskByWeather(new ArrayList<>(), snow());

        assertThat(sections.get(0).getRiskLevel()).isEqualTo("LOW");
    }
}
