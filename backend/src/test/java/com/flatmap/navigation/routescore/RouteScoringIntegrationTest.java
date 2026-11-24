package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.support.CountingDataSource;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 실제 PostGIS 로 경로 주변 시설 집계와 점수를 확인한다. */
@IntegrationTest
class RouteScoringIntegrationTest {

    private static final String INSERT =
            "INSERT INTO safety_facility (facility_type, name, location, data_source) " +
            "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    SafetyFacilityRepository repository;
    @Autowired
    RouteScorer scorer;
    @Autowired
    ScoreModel model;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
    }

    private void add(String type, double lat, double lng) {
        jdbc.update(INSERT, type, type.toLowerCase() + "-" + lat + "-" + lng, lng, lat);
    }

    /**
     * 경로: 경도 127.1000 을 따라 위도 37.4000 → 37.4100 (남북 직선, 길이 약 1,110m).
     * 위도 1° ≈ 110,985m 이므로 0.01° ≈ 1,109.85m. 경도 0.0001° ≈ 8.86m (위도 37.4° 에서).
     * 설정: CCTV·보안등 반경 50m, 경찰 반경 300m.
     */
    private void insertHandCalcFixture() {
        // 경로에서 동쪽 26.6m(0.0003°) — 반경 50m 안 → 센다. CCTV 3개
        add("CCTV", 37.4020, 127.1003);
        add("CCTV", 37.4050, 127.1003);
        add("CCTV", 37.4080, 127.1003);
        // 동쪽 88.6m(0.0010°) — 50m 밖 → 세지 않는다
        add("CCTV", 37.4050, 127.1010);

        // 서쪽 26.6m — 안 → 센다. 보안등 4개
        add("SECURITY_LIGHT", 37.4010, 127.0997);
        add("SECURITY_LIGHT", 37.4030, 127.0997);
        add("SECURITY_LIGHT", 37.4060, 127.0997);
        add("SECURITY_LIGHT", 37.4090, 127.0997);
        // 동쪽 62m(0.0007°) — 50m 밖 → 세지 않는다
        add("SECURITY_LIGHT", 37.4050, 127.1007);

        // 동쪽 177m(0.0020°) — 경찰 반경 300m 안 → 센다 (가중 5)
        add("POLICE", 37.4050, 127.1020);
        // 동쪽 443m(0.0050°) — 300m 밖 → 세지 않는다
        add("POLICE", 37.4050, 127.1050);
    }

    private static List<double[]> meridianRoute() {
        return List.of(new double[]{37.4000, 127.1000}, new double[]{37.4100, 127.1000});
    }

    @Test
    void handCalculatedRouteScoreMatches() {
        insertHandCalcFixture();

        RouteExposure e = repository.countAlongRoute(RouteGeometry.toWkt(meridianRoute()),
                model.radiusMeters(), model.policeRadiusMeters());

        // 시설 수 — 반경 안만 센다
        assertThat(e.cctvCount()).isEqualTo(3);
        assertThat(e.lightCount()).isEqualTo(4);
        assertThat(e.policeCount()).isEqualTo(1);
        // 경로 길이 약 1,109.85m
        assertThat(e.lengthMeters()).isCloseTo(1109.85, within(3.0));

        // 손계산:
        //   가중 시설 수 = CCTV 3 x 1 + 보안등 4 x 1 + 경찰 1 x 5 = 12
        //   km 당 = 12 / 1.10985 = 10.812
        //   구간 함수 (설정 0:0,2:20,5:40,10:60,20:80,40:100) 의 10~20 구간: 60 + (10.812 - 10) x (20 / 10) = 61.62
        RouteScore score = scorer.score(e);
        AxisScore a = score.breakdown().get(0);
        assertThat(a.detail().get("weightedCount")).isEqualTo(12.0);
        assertThat((double) a.detail().get("weightedPerKm")).isCloseTo(10.81, within(0.02));
        assertThat(score.totalScore()).isCloseTo(61.6, within(0.15));
        assertThat(a.score()).isEqualTo(score.totalScore());
    }

    @Test
    void emptyFacilityTableGivesZeroCountsAndStillReturnsLength() {
        RouteExposure e = repository.countAlongRoute(RouteGeometry.toWkt(meridianRoute()), 50, 300);
        assertThat(e.cctvCount()).isZero();
        assertThat(e.lightCount()).isZero();
        assertThat(e.policeCount()).isZero();
        assertThat(e.lengthMeters()).isCloseTo(1109.85, within(3.0));
        assertThat(scorer.score(e).totalScore()).isEqualTo(0.0);
    }

    @Test
    void radiusIsInMetersNotDegrees() {
        // 경도 0.0005° 는 위도 37.4° 에서 약 44m, 위도 0.0005° 는 약 55m 다. 같은 도 단위라도 거리가 다르다.
        add("CCTV", 37.4050, 127.1005);   // 동쪽 약 44m → 50m 안
        add("CCTV", 37.4050, 127.0994);   // 서쪽 약 53m → 50m 밖
        RouteExposure e = repository.countAlongRoute(RouteGeometry.toWkt(meridianRoute()), 50, 300);
        assertThat(e.cctvCount()).isEqualTo(1);
    }

    @Test
    void aRouteWithManyVerticesIsOneSqlStatement() {
        insertHandCalcFixture();
        CountingDataSource.Counted counted = CountingDataSource.wrap(dataSource);
        SafetyFacilityRepository counting = new SafetyFacilityRepository(new JdbcTemplate(counted.dataSource()));

        List<double[]> route = new ArrayList<>();
        for (int k = 0; k <= 400; k++) {            // 정점 401개
            route.add(new double[]{37.4000 + 0.01 * k / 400.0, 127.1000});
        }
        counted.reset();
        RouteExposure e = counting.countAlongRoute(RouteGeometry.toWkt(route), 50, 300);

        assertThat(counted.count()).as("정점 수와 무관하게 쿼리 1회").isEqualTo(1);
        assertThat(e.cctvCount()).isEqualTo(3);
    }

    @Test
    void scoringThreeCandidatesRunsExactlyThreeAggregationQueries() {
        insertHandCalcFixture();
        CountingDataSource.Counted counted = CountingDataSource.wrap(dataSource);
        SafetyFacilityRepository counting = new SafetyFacilityRepository(new JdbcTemplate(counted.dataSource()));

        TMapRouteService tmap = Mockito.mock(TMapRouteService.class);
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of(
                candidate(0, "일반", 37.4100), candidate(4, "대로우선", 37.4100), candidate(10, "최단", 37.4100)));

        SafeRouteService service = new SafeRouteService(tmap, counting, scorer, model);
        counted.reset();
        List<ScoredRoute> routes = service.findScoredRoutes(37.4, 127.1, 37.41, 127.1);

        assertThat(counted.count()).as("경로당 집계 쿼리 1회 (정점 수 무관)").isEqualTo(3);
        assertThat(routes).hasSize(3);
        assertThat(routes).allSatisfy(r -> {
            assertThat(r.cctvCount()).isEqualTo(3);
            assertThat(r.lightCount()).isEqualTo(4);
            assertThat(r.policeCount()).isEqualTo(1);
            assertThat(r.breakdown()).hasSize(1);
        });
        assertThat(routes.stream().filter(ScoredRoute::recommended)).hasSize(1);
    }

    private static TMapRouteService.CandidateRoute candidate(int option, String name, double endLat) {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int k = 0; k <= 50; k++) {
            coords.add(new RouteResponse.Coord(37.4000 + (endLat - 37.4000) * k / 50.0, 127.1000));
        }
        return new TMapRouteService.CandidateRoute(option, name, new RouteResponse(coords, 1110, 800 + option));
    }
}
