package com.flatmap.navigation.walk;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.routescore.AxisScore;
import com.flatmap.navigation.routescore.RouteGeometry;
import com.flatmap.navigation.routescore.RouteScorer;
import com.flatmap.navigation.routescore.SafeRouteService;
import com.flatmap.navigation.routescore.ScoreModel;
import com.flatmap.navigation.routescore.ScoredRoute;
import com.flatmap.navigation.service.SteepSlopeService;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.service.WeatherService;
import com.flatmap.navigation.support.CountingDataSource;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 실제 PostGIS 로 경로 경사 샘플링을 확인한다. 격자 값은 직접 넣은 픽스처다.
 *
 * <p>경로: 경도 127.1000 을 따라 위도 37.4000 → 37.4100(남북 직선, 약 1,110m).
 * 격자 설정(37.33 / 127.04, 셀 중심 간격 1/3600°)에서 이 경로는 셀 열 i=216, 행 j=252~288 을 지난다.
 */
@IntegrationTest
class WalkSlopeSamplingIntegrationTest {

    private static final int COL = 216;               // (127.1000 - 127.04) * 3600
    private static final int ROW0 = 252;              // (37.4000 - 37.33) * 3600

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    WalkSlopeRepository repository;
    @Autowired
    WalkDifficultyService service;
    @Autowired
    WalkAssessor assessor;
    @Autowired
    WalkGrid grid;
    @Autowired
    WalkModel model;
    @Autowired
    RouteScorer scorer;
    @Autowired
    ScoreModel scoreModel;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM walk_slope_cell");
        jdbc.update("DELETE FROM safety_facility");
    }

    /** 열 COL 에서 행 j 의 경사를 {@code slopeOf(j)} 로 채운다(fromRow..toRow). */
    private void fill(int fromRow, int toRow, java.util.function.IntToDoubleFunction slopeOf) {
        SlopeCells cells = new SlopeCells(toRow - fromRow + 1);
        for (int j = fromRow; j <= toRow; j++) cells.add(COL, j, slopeOf.applyAsDouble(j), 100f);
        repository.replaceAll(cells);
    }

    /** 행 구간별 경사: ≤260 평지 1.0° / ≤270 4.0° / ≤280 6.0° / 그 위 8.0°. */
    private static double band(int j) {
        if (j <= 260) return 1.0;
        if (j <= 270) return 4.0;
        if (j <= 280) return 6.0;
        return 8.0;
    }

    private static List<double[]> meridianRoute() {
        return List.of(new double[]{37.4000, 127.1000}, new double[]{37.4100, 127.1000});
    }

    /** 샘플 k(0..37)가 놓이는 행 — 경로를 37등분한 지점의 가장 가까운 셀 중심. */
    private static int rowOfSample(int k) {
        return (int) Math.floor(ROW0 + 36.0 * k / 37 + 0.5);
    }

    @Test
    void samplesFollowTheRouteAndReadTheCellUnderEachPoint() {
        fill(245, 295, WalkSlopeSamplingIntegrationTest::band);

        SlopeProfile p = repository.sample(RouteGeometry.toWkt(meridianRoute()), grid, 30, 2000);

        assertThat(p.lengthMeters()).isCloseTo(1109.85, within(3.0));
        // ⌈1109.85 / 30⌉ = 37 구간 → 샘플 38개(양 끝 포함), 간격 약 30m
        assertThat(p.sampleCount()).isEqualTo(38);
        for (int k = 0; k < 38; k++) {
            assertThat(p.slopes()[k]).as("샘플 %d (행 %d)", k, rowOfSample(k)).isEqualTo(band(rowOfSample(k)));
        }
        assertThat(p.slopes()[0]).isEqualTo(1.0);
        assertThat(p.slopes()[37]).isEqualTo(8.0);
    }

    @Test
    void generalAndWeakModesGiveDifferentResultsForTheSameRoute() {
        fill(245, 295, WalkSlopeSamplingIntegrationTest::band);

        WalkAssessment general = service.assess(meridianRoute(), WalkMode.GENERAL).orElseThrow();
        WalkAssessment weak = service.assess(meridianRoute(), WalkMode.WEAK).orElseThrow();

        // 샘플 38개를 행 구간으로 센다: 평지(≤260) / 4.0°(≤270) / 6.0°(≤280) / 8.0°(그 위)
        int[] n = new int[4];
        for (int k = 0; k < 38; k++) {
            int j = rowOfSample(k);
            n[j <= 260 ? 0 : j <= 270 ? 1 : j <= 280 ? 2 : 3]++;
        }
        assertThat(n[0] + n[1] + n[2] + n[3]).isEqualTo(38);
        // 일반: 양호 = 평지 + 4.0°, 주의 = 6.0°, 위험 = 8.0°
        assertThat(general.goodPercent()).isCloseTo(100.0 * (n[0] + n[1]) / 38, within(0.1));
        assertThat(general.cautionPercent()).isCloseTo(100.0 * n[2] / 38, within(0.1));
        assertThat(general.dangerPercent()).isCloseTo(100.0 * n[3] / 38, within(0.1));
        // 보행약자: 양호 = 평지, 주의 = 4.0°, 위험 = 6.0° + 8.0°
        assertThat(weak.goodPercent()).isCloseTo(100.0 * n[0] / 38, within(0.1));
        assertThat(weak.cautionPercent()).isCloseTo(100.0 * n[1] / 38, within(0.1));
        assertThat(weak.dangerPercent()).isCloseTo(100.0 * (n[2] + n[3]) / 38, within(0.1));

        assertThat(weak.score()).isLessThan(general.score());
        assertThat(general.goodPercent() + general.cautionPercent() + general.dangerPercent())
                .isCloseTo(100.0, within(1e-9));
        assertThat(weak.maxSlopeDeg()).isEqualTo(8.0);
        assertThat(weak.coverage()).isEqualTo(1.0);
    }

    @ParameterizedTest(name = "{0} 경사 {1}° ({2}) → 100% {3}")
    @CsvSource({
            "WEAK, 3.18, EXACT, GOOD", "WEAK, 3.18, BELOW, GOOD", "WEAK, 3.18, ABOVE, CAUTION",
            "WEAK, 4.76, EXACT, CAUTION", "WEAK, 4.76, BELOW, CAUTION", "WEAK, 4.76, ABOVE, DANGER",
            "GENERAL, 4.76, EXACT, GOOD", "GENERAL, 4.76, BELOW, GOOD", "GENERAL, 4.76, ABOVE, CAUTION",
            "GENERAL, 7.13, EXACT, CAUTION", "GENERAL, 7.13, BELOW, CAUTION", "GENERAL, 7.13, ABOVE, DANGER"})
    void boundaryValuesSurviveTheDatabaseRoundTrip(WalkMode mode, double boundary, String side, SlopeClass expected) {
        double slope = switch (side) {
            case "BELOW" -> Math.nextDown(boundary);
            case "ABOVE" -> Math.nextUp(boundary);
            default -> boundary;
        };
        fill(ROW0, ROW0, j -> slope);
        // 셀 하나(행 252) 안에 머무르는 짧은 경로(약 11m)
        List<double[]> tiny = List.of(new double[]{37.40000, 127.1000}, new double[]{37.40010, 127.1000});

        WalkAssessment a = service.assess(tiny, mode).orElseThrow();

        assertThat(a.goodPercent()).isEqualTo(expected == SlopeClass.GOOD ? 100.0 : 0.0);
        assertThat(a.cautionPercent()).isEqualTo(expected == SlopeClass.CAUTION ? 100.0 : 0.0);
        assertThat(a.dangerPercent()).isEqualTo(expected == SlopeClass.DANGER ? 100.0 : 0.0);
    }

    @Test
    void aRouteIsOneSqlStatementNoMatterHowManyVertices() {
        fill(245, 295, WalkSlopeSamplingIntegrationTest::band);
        CountingDataSource.Counted counted = CountingDataSource.wrap(dataSource);
        WalkSlopeRepository counting = new WalkSlopeRepository(new JdbcTemplate(counted.dataSource()));
        WalkDifficultyService countingService = new WalkDifficultyService(counting, assessor, grid, model);

        List<double[]> route = new ArrayList<>();
        for (int k = 0; k <= 400; k++) {                       // 정점 401개
            route.add(new double[]{37.4000 + 0.01 * k / 400.0, 127.1000});
        }
        counted.reset();
        WalkAssessment a = countingService.assess(route, WalkMode.GENERAL).orElseThrow();

        assertThat(counted.count()).as("정점·샘플 수와 무관하게 쿼리 1회").isEqualTo(1);
        assertThat(a.sampleCount()).isEqualTo(38);
    }

    @Test
    void sampleCountIsCappedForVeryLongRoutes() {
        SlopeProfile p = repository.sample(RouteGeometry.toWkt(meridianRoute()), grid, 30, 10);
        assertThat(p.sampleCount()).isEqualTo(11);
    }

    @Test
    void noGridDataMeansNoAssessment() {
        // 격자가 비어 있다
        assertThat(service.assess(meridianRoute(), WalkMode.GENERAL)).isEmpty();
        // 격자는 있지만 경로가 성남 범위 밖(서울)이다
        fill(245, 295, WalkSlopeSamplingIntegrationTest::band);
        List<double[]> seoul = List.of(new double[]{37.5665, 126.9780}, new double[]{37.5700, 126.9800});
        assertThat(service.assess(seoul, WalkMode.GENERAL)).isEmpty();
    }

    @Test
    void routeMostlyOutsideTheGridIsNotAssessedButAMostlyCoveredOneUsesOnlyCoveredSamples() {
        // 행 252~266 만 값이 있다 → 샘플 38개 중 약 15개(39%)만 값이 있어 최소 커버리지(50%) 미달
        fill(ROW0, 266, WalkSlopeSamplingIntegrationTest::band);
        assertThat(service.assess(meridianRoute(), WalkMode.GENERAL)).isEmpty();

        // 행 252~280 → 샘플 약 29개(76%): 평가하며 비율은 값이 있는 샘플 기준
        fill(ROW0, 280, WalkSlopeSamplingIntegrationTest::band);
        WalkAssessment a = service.assess(meridianRoute(), WalkMode.GENERAL).orElseThrow();
        long covered = java.util.stream.IntStream.range(0, 38).filter(k -> rowOfSample(k) <= 280).count();
        assertThat(a.dataCount()).isEqualTo((int) covered);
        assertThat(a.coverage()).isCloseTo(covered / 38.0, within(1e-9));
        assertThat(a.dangerPercent()).isZero();                 // 8.0° 구간(행 281~)은 격자 밖이라 위험으로 채우지 않는다
    }

    @Test
    void degenerateRoutesDoNotBreakTheQuery() {
        fill(ROW0, ROW0, j -> 1.0);
        List<double[]> samePoint = List.of(new double[]{37.4000, 127.1000}, new double[]{37.4000, 127.1000});
        WalkAssessment a = service.assess(samePoint, WalkMode.GENERAL).orElseThrow();
        assertThat(a.goodPercent()).isEqualTo(100.0);
        // 점 하나뿐인 경로는 평가하지 않고 쿼리도 하지 않는다
        assertThat(service.assess(List.of(new double[]{37.4000, 127.1000}), WalkMode.GENERAL)).isEmpty();
        assertThat(service.assess(List.of(), WalkMode.GENERAL)).isEmpty();
    }

    // ---- 점수에 B축으로 합류 ----

    private static TMapRouteService.CandidateRoute candidate(int option, String name, double endLat) {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int k = 0; k <= 50; k++) {
            coords.add(new RouteResponse.Coord(37.4000 + (endLat - 37.4000) * k / 50.0, 127.1000));
        }
        return new TMapRouteService.CandidateRoute(option, name, new RouteResponse(coords, 1110, 800 + option));
    }

    @Test
    void candidatesGetABAxisWithTheModeAndEachRouteCostsOneSlopeQuery() {
        fill(245, 295, WalkSlopeSamplingIntegrationTest::band);
        CountingDataSource.Counted counted = CountingDataSource.wrap(dataSource);
        JdbcTemplate countingJdbc = new JdbcTemplate(counted.dataSource());

        TMapRouteService tmap = Mockito.mock(TMapRouteService.class);
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of(
                candidate(0, "일반", 37.4100), candidate(4, "대로우선", 37.4100), candidate(10, "최단", 37.4100)));
        WeatherService weather = Mockito.mock(WeatherService.class);
        Mockito.when(weather.getWeather(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(WeatherInfo.fallback());

        SafeRouteService safe = new SafeRouteService(tmap, new SafetyFacilityRepository(countingJdbc), scorer,
                scoreModel, weather, new SteepSlopeService(new SteepSlopeAreaRepository(countingJdbc)),
                new WalkDifficultyService(new WalkSlopeRepository(countingJdbc), assessor, grid, model));

        counted.reset();
        List<ScoredRoute> general = safe.findScoredRoutes(37.4, 127.1, 37.41, 127.1, WalkMode.GENERAL);
        // 경로마다 시설 집계 1 + 경사 샘플링 1 + 급경사지 경고 1 = 3문, 후보 3개
        assertThat(counted.count()).isEqualTo(9);

        List<ScoredRoute> weak = safe.findScoredRoutes(37.4, 127.1, 37.41, 127.1, WalkMode.WEAK);
        for (int i = 0; i < 3; i++) {
            ScoredRoute g = general.get(i), w = weak.get(i);
            assertThat(g.breakdown()).extracting(AxisScore::axis).containsExactly("A", "B");   // 날씨 대체값 → C 없음
            AxisScore b = g.breakdown().get(1);
            assertThat(b.name()).isEqualTo("보행난이도");
            assertThat(b.weight()).isEqualTo(scoreModel.axisWeightB());
            assertThat(b.detail()).containsEntry("mode", "GENERAL");
            assertThat(w.breakdown().get(1).detail()).containsEntry("mode", "WEAK");
            // 총점 = A·B 의 가중 평균(각각 소수 첫째 자리)
            AxisScore a = g.breakdown().get(0);
            assertThat(g.totalScore()).isCloseTo(
                    (a.score() * a.weight() + b.score() * b.weight()) / (a.weight() + b.weight()), within(0.06));
            // 같은 경로가 보행약자 기준에서 더 낮다
            assertThat(w.breakdown().get(1).score()).isLessThan(b.score());
            assertThat(w.totalScore()).isLessThan(g.totalScore());
            // 급경사지(붕괴위험)는 이 점수와 무관한 별도 경고다
            assertThat(g.riskSections()).isEmpty();
        }
        Map<String, Object> detail = general.get(0).breakdown().get(1).detail();
        assertThat(detail).containsKey("limitation");
    }

    @Test
    void routesWithoutSlopeDataKeepTheOldAxesOnly() {
        // 격자가 비어 있다 → B축은 breakdown 에 없고 점수는 A축만으로 계산된다(0점·100점으로 채우지 않는다)
        TMapRouteService tmap = Mockito.mock(TMapRouteService.class);
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of(candidate(0, "일반", 37.4100)));
        WeatherService weather = Mockito.mock(WeatherService.class);
        Mockito.when(weather.getWeather(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(WeatherInfo.fallback());
        SafeRouteService safe = new SafeRouteService(tmap, new SafetyFacilityRepository(jdbc), scorer, scoreModel,
                weather, new SteepSlopeService(new SteepSlopeAreaRepository(jdbc)), service);

        ScoredRoute r = safe.findScoredRoutes(37.4, 127.1, 37.41, 127.1, WalkMode.WEAK).get(0);

        assertThat(r.breakdown()).extracting(AxisScore::axis).containsExactly("A");
        assertThat(r.totalScore()).isEqualTo(r.breakdown().get(0).score());
    }
}
