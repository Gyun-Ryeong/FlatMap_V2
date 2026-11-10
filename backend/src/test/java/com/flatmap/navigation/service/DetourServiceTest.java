package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 우회 경유지 생성(DetourService.generatePassList)의 동작 고정 테스트.
 * 경유지 후보가 다른 급경사지 반경(200m)에 걸리는지 판정하는 부분을 DB 쿼리로 바꿔도 결과가 같아야 한다.
 *
 * <p>픽스처: 위도 37.4000 을 따라 동쪽으로 가는 경로(경도 127.100~127.120, 0.0005° 간격, 좌표 41개).
 * 경로 진행 방향이 동쪽이면 "시계방향 후보"는 북쪽, 다른 후보는 남쪽이다. 위도 1° ≈ 111,000m 로 계산한다.
 */
@IntegrationTest
class DetourServiceTest {

    private static final double ROUTE_LAT = 37.4000;

    @Autowired
    DetourService detourService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM steep_slope_areas");
    }

    private static List<RouteResponse.Coord> eastRoute() {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            coords.add(new RouteResponse.Coord(ROUTE_LAT, 127.1000 + i * 0.0005));
        }
        return coords;
    }

    private void insertArea(String name, double lat, double lng) {
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) " +
                        "VALUES (?, 5.0, 'TEST', ST_SetSRID(ST_MakePoint(?, ?), 4326))",
                name, lng, lat);
    }

    private static RiskSection risk(double lat, double lng, int routeIdx) {
        return new RiskSection(lat, lng, "h", 5.0, null, 100, routeIdx);
    }

    @Test
    void nullOrEmptyRisksYieldNoPassList() {
        assertThat(detourService.generatePassList(null, eastRoute())).isNull();
        assertThat(detourService.generatePassList(List.of(), eastRoute())).isNull();
    }

    @Test
    void waypointIsPushedToTheSideWithoutAnotherHazard() {
        // 위험 지점은 경로 북쪽 100m. 북쪽 후보(250m)는 위험 지점에서 150m 라 반경에 걸리고, 남쪽 후보(350m)는 통과한다.
        insertArea("H", ROUTE_LAT + 0.0009, 127.1100);

        String passList = detourService.generatePassList(
                List.of(risk(ROUTE_LAT + 0.0009, 127.1100, 20)), eastRoute());

        assertThat(passList).isEqualTo("127.110000,37.397748");   // lng,lat — 경로 남쪽 250m
    }

    @Test
    void offsetGrowsWhenTheFirstStepIsBlockedOnBothSides() {
        // 북쪽 250m 는 H 에, 남쪽 250m 는 S 에 걸린다. 350m 단계에서는 북쪽만 통과한다.
        insertArea("H", ROUTE_LAT + 0.0009, 127.1100);
        insertArea("S", ROUTE_LAT - 0.00225, 127.1100);

        String passList = detourService.generatePassList(
                List.of(risk(ROUTE_LAT + 0.0009, 127.1100, 20)), eastRoute());

        assertThat(passList).isEqualTo("127.110000,37.403153");   // 경로 북쪽 350m
    }

    @Test
    void noWaypointWhenEveryCandidateIsBlocked() {
        insertArea("H", ROUTE_LAT + 0.0009, 127.1100);
        // 경로 양쪽 150~750m 구간을 100m 간격으로 막아 모든 오프셋(250/350/450/600m) 후보가 반경에 걸리게 한다.
        for (int k = 0; k <= 6; k++) {
            double dLat = (150.0 + 100.0 * k) / 111000.0;
            insertArea("N" + k, ROUTE_LAT + dLat, 127.1100);
            insertArea("S" + k, ROUTE_LAT - dLat, 127.1100);
        }

        String passList = detourService.generatePassList(
                List.of(risk(ROUTE_LAT + 0.0009, 127.1100, 20)), eastRoute());

        assertThat(passList).isNull();
    }

    @Test
    void separateClustersGetOneWaypointEachInRouteOrder() {
        insertArea("H1", ROUTE_LAT + 0.0009, 127.1050);
        insertArea("H2", ROUTE_LAT + 0.0009, 127.1150);

        String passList = detourService.generatePassList(List.of(
                risk(ROUTE_LAT + 0.0009, 127.1150, 30),
                risk(ROUTE_LAT + 0.0009, 127.1050, 10)), eastRoute());

        assertThat(passList).isEqualTo("127.105000,37.397748_127.115000,37.397748");
    }

    @Test
    void nearbySectionsAreMergedIntoOneWaypoint() {
        // 서로 300m 안(약 133m)에 있는 두 위험 지점은 하나의 장애물로 본다.
        insertArea("H1", ROUTE_LAT + 0.0009, 127.1100);
        insertArea("H2", ROUTE_LAT + 0.0009, 127.1115);

        String passList = detourService.generatePassList(List.of(
                risk(ROUTE_LAT + 0.0009, 127.1100, 20),
                risk(ROUTE_LAT + 0.0009, 127.1115, 23)), eastRoute());

        assertThat(passList).isNotNull().doesNotContain("_");
    }
}
