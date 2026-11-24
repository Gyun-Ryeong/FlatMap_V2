package com.flatmap.navigation.web;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.service.RouteProviderException;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.service.WeatherService;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/route/walk, /api/route/walk/detour — T Map·기상청은 모킹하고 DB(PostGIS)와 우회 경유지 계산은 실제를 쓴다.
 *
 * <p>픽스처: 위도 37.4000 을 따라 동쪽으로 가는 원경로(경도 127.100~127.120). 급경사지는 경로 북쪽 100m(37.4009, 127.110).
 * 우회 경로는 남쪽 약 400m(위도 37.3964)를 도는 경로라 그 급경사지와 200m 넘게 떨어져 있다.
 */
@IntegrationTest
class WalkRouteEndpointTest {

    private static final double ROUTE_LAT = 37.4000;
    private static final double DETOUR_LAT = 37.3964;

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    TMapRouteService tmap;
    @MockBean
    WeatherService weatherService;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
        jdbc.update("DELETE FROM steep_slope_areas");
        Mockito.reset(tmap, weatherService);
        Mockito.when(weatherService.getWeather(Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(WeatherInfo.fallback());
    }

    private static RouteResponse eastRoute(double lat, int distance) {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            coords.add(new RouteResponse.Coord(lat, 127.1000 + i * 0.0005));
        }
        return new RouteResponse(coords, distance, distance / 2);
    }

    private static WeatherInfo rain() {
        WeatherInfo w = new WeatherInfo();
        w.setPrecipitationType(1);
        w.setTemperature(12);
        w.setWindSpeed(2);
        w.setDescription("비, 12℃");
        w.setIcon("rain");
        return w;
    }

    private void insertSteepSlope(double lat, double lng) {
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) " +
                "VALUES ('H', 5.0, 'TEST', ST_SetSRID(ST_MakePoint(?, ?), 4326))", lng, lat);
    }

    private void insertCctvAlongOriginalRoute(int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                            "VALUES ('CCTV', 'x', ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')",
                    127.1000 + i * 0.0005, ROUTE_LAT);
        }
    }

    private void tmapReturns(RouteResponse original, RouteResponse detour) {
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any())).thenReturn(original);
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any(), Mockito.anyString())).thenReturn(detour);
    }

    private static final String WALK_BODY = """
            {"originLat":37.4,"originLng":127.10,"destLat":37.4,"destLng":127.12,"option":"SAFE"}""";
    private static final String DETOUR_BODY = """
            {"originLat":37.4,"originLng":127.10,"destLat":37.4,"destLng":127.12,"option":"SAFE",
             "avoidPoints":[{"lat":37.4009,"lng":127.11,"nearestRouteIdx":20}]}""";

    // ---- 보행 유형(B축) ----

    /** 원경로(위도 37.4000, 경도 127.100~127.120)가 지나는 행 252 의 열 210~295 를 같은 경사로 채운다. */
    private void insertSlopeAlongOriginalRoute(double slopeDeg) {
        for (int i = 210; i <= 295; i++) {
            jdbc.update("INSERT INTO walk_slope_cell (cell_i, cell_j, slope_deg) VALUES (?, 252, ?)", i, slopeDeg);
        }
    }

    private static String withWalkMode(String body, String mode) {
        return body.replace("\"option\"", "\"walkMode\":\"" + mode + "\",\"option\"");
    }

    @Test
    void walkModeInTheRequestChangesTheBAxisAndTheTotal() throws Exception {
        insertSlopeAlongOriginalRoute(4.0);        // 일반: 양호 / 보행약자: 주의
        tmapReturns(eastRoute(ROUTE_LAT, 1800), null);
        try {
            mvc.perform(post("/api/route/walk").contentType(MediaType.APPLICATION_JSON)
                            .content(withWalkMode(WALK_BODY, "WEAK")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.breakdown.length()").value(2))
                    .andExpect(jsonPath("$.breakdown[1].axis").value("B"))
                    .andExpect(jsonPath("$.breakdown[1].detail.mode").value("WEAK"))
                    .andExpect(jsonPath("$.breakdown[1].score").value(50.0));
            // 모드를 안 주면 기본값 GENERAL
            mvc.perform(post("/api/route/walk").contentType(MediaType.APPLICATION_JSON).content(WALK_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.breakdown[1].detail.mode").value("GENERAL"))
                    .andExpect(jsonPath("$.breakdown[1].score").value(100.0));
        } finally {
            jdbc.update("DELETE FROM walk_slope_cell");
        }
    }

    @Test
    void unknownWalkModeIs400WithoutCallingTheRouteProvider() throws Exception {
        mvc.perform(post("/api/route/walk").contentType(MediaType.APPLICATION_JSON)
                        .content(withWalkMode(WALK_BODY, "turbo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WALK_MODE"));
        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON)
                        .content(withWalkMode(DETOUR_BODY, "turbo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WALK_MODE"));
        Mockito.verifyNoInteractions(tmap);
    }

    @Test
    void detourEvaluatesBothRoutesWithTheRequestedWalkMode() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        insertSlopeAlongOriginalRoute(4.0);        // 원경로는 격자 안, 우회 경로(남쪽 400m)는 격자 밖이다
        tmapReturns(eastRoute(ROUTE_LAT, 1800), eastRoute(DETOUR_LAT, 2300));
        try {
            mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON)
                            .content(withWalkMode(DETOUR_BODY, "WEAK")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalTotalScore").isNumber())
                    .andExpect(jsonPath("$.totalScore").isNumber());
        } finally {
            jdbc.update("DELETE FROM walk_slope_cell");
        }
    }

    // ---- /walk ----

    @Test
    void walkResponseCarriesBreakdownWeatherAndTheSteepSlopeWarningLayer() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        Mockito.when(weatherService.getWeather(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(rain());
        tmapReturns(eastRoute(ROUTE_LAT, 1800), null);

        mvc.perform(post("/api/route/walk").contentType(MediaType.APPLICATION_JSON).content(WALK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.breakdown.length()").value(2))
                .andExpect(jsonPath("$.breakdown[0].axis").value("A"))
                .andExpect(jsonPath("$.breakdown[1].axis").value("C"))
                .andExpect(jsonPath("$.breakdown[1].detail.condition").value("RAIN"))
                .andExpect(jsonPath("$.riskSections.length()").value(1))
                .andExpect(jsonPath("$.riskSections[0].riskLevel").doesNotExist())          // 급경사지는 등급·점수가 없다
                .andExpect(jsonPath("$.weather.fallback").value(false))
                .andExpect(jsonPath("$.originalTotalScore").doesNotExist());
    }

    @Test
    void walkProviderFailureIsAGeneric502() throws Exception {
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any()))
                .thenThrow(new RouteProviderException("경로 검색에 실패했습니다.",
                        new RuntimeException("https://apis.openapi.sk.com/x appKey=SECRET")));

        mvc.perform(post("/api/route/walk").contentType(MediaType.APPLICATION_JSON).content(WALK_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("appKey"))))
                .andExpect(content().string(not(containsString("openapi"))));
    }

    // ---- /walk/detour ----

    @Test
    void improvedDetourIsReturnedWithAxisBreakdownAndTheOriginalScore() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        Mockito.when(weatherService.getWeather(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(rain());
        tmapReturns(eastRoute(ROUTE_LAT, 1800), eastRoute(DETOUR_LAT, 2300));

        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON).content(DETOUR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourApplied").value(true))
                .andExpect(jsonPath("$.totalDistance").value(2300))
                .andExpect(jsonPath("$.riskSections.length()").value(0))
                .andExpect(jsonPath("$.breakdown.length()").value(2))
                .andExpect(jsonPath("$.breakdown[0].axis").value("A"))
                .andExpect(jsonPath("$.breakdown[1].axis").value("C"))
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.originalTotalScore").isNumber());
    }

    @Test
    void detourWithoutImprovementReturnsTheOriginalRouteWithItsBreakdown() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        // 우회라고 받은 경로도 같은 길 → 급경사지 구간이 줄지 않는다
        tmapReturns(eastRoute(ROUTE_LAT, 1800), eastRoute(ROUTE_LAT, 1800));

        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON).content(DETOUR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourApplied").value(false))
                .andExpect(jsonPath("$.detourMessage").value(containsString("원래 경로를 반환")))
                .andExpect(jsonPath("$.totalDistance").value(1800))
                .andExpect(jsonPath("$.riskSections.length()").value(1))
                .andExpect(jsonPath("$.breakdown.length()").value(1))      // 날씨 fallback → A축만
                .andExpect(jsonPath("$.breakdown[0].axis").value("A"));
    }

    @Test
    void detourIsAdoptedByRiskSectionsAloneAndTheLowerScoreIsReportedNotHidden() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        insertCctvAlongOriginalRoute(21);        // 원경로에만 CCTV 21개(약 12/km → A 약 64점), 우회 경로는 0점
        tmapReturns(eastRoute(ROUTE_LAT, 1800), eastRoute(DETOUR_LAT, 2300));

        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON).content(DETOUR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourApplied").value(true))
                .andExpect(jsonPath("$.totalDistance").value(2300))
                .andExpect(jsonPath("$.riskSections.length()").value(0))
                // 우회 경로는 시설이 없어 점수가 크게 낮지만 채택되고, 두 점수가 모두 내려간다
                .andExpect(jsonPath("$.originalTotalScore").value(org.hamcrest.Matchers.greaterThan(50.0)))
                .andExpect(jsonPath("$.totalScore").value(0.0));
    }

    @Test
    void detourRouteProviderFailureFallsBackToTheOriginalRoute() throws Exception {
        insertSteepSlope(ROUTE_LAT + 0.0009, 127.1100);
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any())).thenReturn(eastRoute(ROUTE_LAT, 1800));
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any(), Mockito.anyString()))
                .thenThrow(new RouteProviderException("경로 검색에 실패했습니다.",
                        new RuntimeException("appKey=SECRET")));

        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON).content(DETOUR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourApplied").value(false))
                .andExpect(jsonPath("$.detourMessage").value("우회 경로를 가져오지 못해 원래 경로를 반환했습니다."))
                .andExpect(jsonPath("$.breakdown[0].axis").value("A"))
                .andExpect(content().string(not(containsString("SECRET"))));
    }

    @Test
    void originalRouteProviderFailureOnDetourIsAGeneric502() throws Exception {
        Mockito.when(tmap.getWalkingRoute(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.any()))
                .thenThrow(new RouteProviderException("경로 검색에 실패했습니다.",
                        new RuntimeException("appKey=SECRET")));

        mvc.perform(post("/api/route/walk/detour").contentType(MediaType.APPLICATION_JSON).content(DETOUR_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("SECRET"))));
    }
}
