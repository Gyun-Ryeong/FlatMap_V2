package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.DetourRequest;
import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.routescore.AxisScore;
import com.flatmap.navigation.routescore.RouteExposure;
import com.flatmap.navigation.routescore.RouteScore;
import com.flatmap.navigation.routescore.SafeRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 우회 채택 판단(DetourRouteService)의 규칙 테스트. 협력 객체는 모두 모킹한다 — 판단 규칙만 본다.
 * 규칙: 경유지가 있고, T Map 이 우회 경로를 주고, 급경사지 구간이 줄고, 점수가 허용 폭(10점) 넘게 떨어지지 않아야 우회.
 */
class DetourRouteServiceTest {

    private static final double MAX_DROP = 10.0;

    private TMapRouteService tmap;
    private SteepSlopeService steep;
    private DetourService detour;
    private SafeRouteService safeRoute;
    private DetourRouteService service;

    private RouteResponse original;
    private RouteResponse detoured;

    private static RouteResponse route(double lat, int distance) {
        return new RouteResponse(List.of(new RouteResponse.Coord(lat, 127.10), new RouteResponse.Coord(lat, 127.11),
                new RouteResponse.Coord(lat, 127.12)), distance, distance / 2);
    }

    private static RiskSection risk(int idx) {
        return new RiskSection(37.4, 127.11, "area" + idx, null, "TEST", 100, idx);
    }

    /** A 점수만 있는 평가 결과(날씨 fallback). */
    private static SafeRouteService.Evaluation eval(double total) {
        AxisScore a = new AxisScore("A", "야간 시설 노출", total, 1.0, Map.of());
        return new SafeRouteService.Evaluation(new RouteScore(total, List.of(a)),
                new RouteExposure(0, 0, 0, 1000), WeatherInfo.fallback());
    }

    private static DetourRequest request() {
        DetourRequest r = new DetourRequest();
        r.setOriginLat(37.4);
        r.setOriginLng(127.10);
        r.setDestLat(37.4);
        r.setDestLng(127.12);
        r.setOption("SAFE");
        DetourRequest.RiskCoord p = new DetourRequest.RiskCoord();
        p.setLat(37.4009);
        p.setLng(127.11);
        p.setNearestRouteIdx(1);
        r.setAvoidPoints(List.of(p));
        return r;
    }

    @BeforeEach
    void setUp() {
        tmap = mock(TMapRouteService.class);
        steep = mock(SteepSlopeService.class);
        detour = mock(DetourService.class);
        safeRoute = mock(SafeRouteService.class);
        service = new DetourRouteService(tmap, steep, detour, safeRoute, MAX_DROP);

        original = route(37.4000, 1800);
        detoured = route(37.3964, 2300);
        when(tmap.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(original);
        when(tmap.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyString()))
                .thenReturn(detoured);
        when(detour.generatePassList(anyList(), anyList())).thenReturn("127.110000,37.397748");
        // 기본: 원경로 급경사지 구간 2건, 우회 경로 0건, 점수는 같다
        when(steep.findRiskSections(original.getCoords())).thenReturn(List.of(risk(1), risk(2)));
        when(steep.findRiskSections(detoured.getCoords())).thenReturn(List.of());
        scores(60.0, 60.0);
    }

    /** 원경로와 우회 경로의 총점을 각각 지정한다(좌표 위도로 구분). */
    private void scores(double originalTotal, double detourTotal) {
        // doAnswer: when(...) 로 다시 지정하면 이전 Answer 가 빈 목록으로 한 번 호출된다
        doAnswer(inv -> {
            List<double[]> pts = inv.getArgument(0);
            return pts.get(0)[0] > 37.399 ? eval(originalTotal) : eval(detourTotal);
        }).when(safeRoute).evaluate(anyList(), any());
    }

    @Test
    void improvedDetourIsReturnedWithBreakdownAndTheOriginalScoreForComparison() {
        RouteResponse r = service.detour(request());

        assertThat(r).isSameAs(detoured);
        assertThat(r.isDetourApplied()).isTrue();
        assertThat(r.getDetourMessage()).isNull();
        assertThat(r.getRiskSections()).isEmpty();
        assertThat(r.getTotalScore()).isEqualTo(60.0);
        assertThat(r.getOriginalTotalScore()).isEqualTo(60.0);
        assertThat(r.getBreakdown()).extracting(AxisScore::axis).containsExactly("A");
        assertThat(r.getWeather()).isNotNull();
    }

    @Test
    void noImprovementInRiskSectionsReturnsTheOriginalRoute() {
        // 우회 경로도 2건 → 줄지 않음
        when(steep.findRiskSections(detoured.getCoords())).thenReturn(List.of(risk(1), risk(2)));

        RouteResponse r = service.detour(request());

        assertThat(r).isSameAs(original);
        assertThat(r.isDetourApplied()).isFalse();
        assertThat(r.getDetourMessage()).contains("원래 경로를 반환");
        assertThat(r.getRiskSections()).hasSize(2);
        assertThat(r.getBreakdown()).isNotEmpty();                 // 원경로에도 breakdown 이 있다
        assertThat(r.getTotalScore()).isEqualTo(60.0);
        // 점수 비교까지 가지 않는다 — 원경로 한 번만 평가하고 우회 경로의 시설 집계 쿼리는 아낀다
        verify(safeRoute, times(1)).evaluate(anyList(), any());
    }

    @Test
    void moreRiskSectionsOnTheDetourAlsoReturnsTheOriginal() {
        when(steep.findRiskSections(detoured.getCoords())).thenReturn(List.of(risk(1), risk(2), risk(3)));

        assertThat(service.detour(request())).isSameAs(original);
    }

    @Test
    void scoreDropExactlyAtTheToleranceStillAcceptsTheDetour() {
        scores(70.0, 60.0);     // 정확히 10점 하락 = 허용 폭

        RouteResponse r = service.detour(request());

        assertThat(r.isDetourApplied()).isTrue();
        assertThat(r.getTotalScore()).isEqualTo(60.0);
        assertThat(r.getOriginalTotalScore()).isEqualTo(70.0);
    }

    @Test
    void scoreDropBeyondTheToleranceReturnsTheOriginal() {
        scores(70.0, 59.9);     // 10.1점 하락 → 허용 폭 초과

        RouteResponse r = service.detour(request());

        assertThat(r).isSameAs(original);
        assertThat(r.isDetourApplied()).isFalse();
        assertThat(r.getDetourMessage()).contains("점수가 크게 낮아져");
        assertThat(r.getTotalScore()).isEqualTo(70.0);
        assertThat(r.getOriginalTotalScore()).isEqualTo(70.0);
        assertThat(r.getRiskSections()).hasSize(2);
    }

    @Test
    void aDetourWithAHigherScoreIsAccepted() {
        scores(50.0, 80.0);

        RouteResponse r = service.detour(request());

        assertThat(r.isDetourApplied()).isTrue();
        assertThat(r.getTotalScore()).isEqualTo(80.0);
        assertThat(r.getOriginalTotalScore()).isEqualTo(50.0);
    }

    @Test
    void toleranceIsAConstructorParameterNotAConstant() {
        scores(70.0, 59.9);
        DetourRouteService lenient = new DetourRouteService(tmap, steep, detour, safeRoute, 15.0);

        assertThat(lenient.detour(request()).isDetourApplied()).isTrue();
    }

    @Test
    void negativeToleranceIsRejected() {
        assertThatThrownBy(() -> new DetourRouteService(tmap, steep, detour, safeRoute, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detourRouteProviderFailureFallsBackToTheOriginalWithAGenericMessage() {
        when(tmap.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyString()))
                .thenThrow(new RouteProviderException("경로 검색에 실패했습니다.",
                        new RuntimeException("secret internal detail https://apis.example/?appKey=XYZ")));

        RouteResponse r = service.detour(request());

        assertThat(r).isSameAs(original);
        assertThat(r.isDetourApplied()).isFalse();
        assertThat(r.getDetourMessage()).doesNotContain("secret").doesNotContain("appKey").doesNotContain("http");
        assertThat(r.getBreakdown()).isNotEmpty();
    }

    @Test
    void originalRouteProviderFailurePropagatesForTheControllerToGeneralize() {
        when(tmap.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenThrow(new RouteProviderException("경로 검색에 실패했습니다."));

        assertThatThrownBy(() -> service.detour(request())).isInstanceOf(RouteProviderException.class);
        verify(tmap, never()).getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyString());
    }

    @Test
    void noWaypointMeansTheOriginalRouteWithoutCallingTMapAgain() {
        when(detour.generatePassList(anyList(), anyList())).thenReturn(null);

        RouteResponse r = service.detour(request());

        assertThat(r).isSameAs(original);
        assertThat(r.isDetourApplied()).isFalse();
        assertThat(r.getDetourMessage()).contains("안전 지점을 찾지 못해");
        verify(tmap, never()).getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyString());
    }

    @Test
    void missingAvoidPointsDoNotCrashAndReturnTheOriginal() {
        DetourRequest r = request();
        r.setAvoidPoints(null);
        when(detour.generatePassList(eq(List.of()), anyList())).thenReturn(null);

        RouteResponse res = service.detour(r);

        assertThat(res.isDetourApplied()).isFalse();
        assertThat(res.getTotalScore()).isNotNull();
    }

    @Test
    void singleRouteWalkCarriesRiskSectionsWeatherAndBreakdown() {
        com.flatmap.navigation.dto.RouteRequest req = new com.flatmap.navigation.dto.RouteRequest();
        req.setOption("SAFE");

        RouteResponse r = service.walk(req);

        assertThat(r).isSameAs(original);
        assertThat(r.getRiskSections()).hasSize(2);
        assertThat(r.getTotalScore()).isEqualTo(60.0);
        assertThat(r.getBreakdown()).hasSize(1);
        assertThat(r.getOriginalTotalScore()).isNull();            // 단일 경로에는 비교 대상이 없다
    }
}
