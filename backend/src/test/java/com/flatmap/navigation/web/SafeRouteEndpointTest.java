package com.flatmap.navigation.web;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/routes/safe — T Map 은 모킹하고 DB 는 실제 PostGIS 를 쓴다. */
@IntegrationTest
class SafeRouteEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    TMapRouteService tmap;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
        Mockito.reset(tmap);
    }

    private static TMapRouteService.CandidateRoute candidate(int option, String name, int time) {
        List<RouteResponse.Coord> coords = new ArrayList<>();
        for (int k = 0; k <= 10; k++) {
            coords.add(new RouteResponse.Coord(37.4000 + 0.01 * k / 10.0, 127.1000));
        }
        return new TMapRouteService.CandidateRoute(option, name, new RouteResponse(coords, 1110, time));
    }

    private void addFacility(String type, double lat, double lng) {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES (?, 'x', ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')", type, lng, lat);
    }

    private static final String QUERY = "?startLat=37.40&startLng=127.10&endLat=37.41&endLng=127.10";

    @Test
    void responseHasTotalScoreBreakdownAndFacilityCounts() throws Exception {
        addFacility("CCTV", 37.4050, 127.1003);
        addFacility("SECURITY_LIGHT", 37.4030, 127.0997);
        addFacility("POLICE", 37.4050, 127.1020);
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(List.of(candidate(0, "일반", 800), candidate(10, "최단", 700)));

        mvc.perform(get("/api/routes/safe" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(2))
                .andExpect(jsonPath("$.routes[0].routeId").isNotEmpty())
                .andExpect(jsonPath("$.routes[0].searchOption").value("일반"))
                .andExpect(jsonPath("$.routes[0].searchOptionCode").value(0))
                .andExpect(jsonPath("$.routes[0].totalDistance").value(1110))
                .andExpect(jsonPath("$.routes[0].totalTime").value(800))
                .andExpect(jsonPath("$.routes[0].coordinates.length()").value(11))
                .andExpect(jsonPath("$.routes[0].totalScore").isNumber())
                .andExpect(jsonPath("$.routes[0].cctvCount").value(1))
                .andExpect(jsonPath("$.routes[0].lightCount").value(1))
                .andExpect(jsonPath("$.routes[0].policeCount").value(1))
                .andExpect(jsonPath("$.routes[0].breakdown.length()").value(1))
                .andExpect(jsonPath("$.routes[0].breakdown[0].axis").value("A"))
                .andExpect(jsonPath("$.routes[0].breakdown[0].name").value("야간 시설 노출"))
                .andExpect(jsonPath("$.routes[0].breakdown[0].score").isNumber())
                .andExpect(jsonPath("$.routes[0].breakdown[0].weight").value(1.0))
                .andExpect(jsonPath("$.routes[0].breakdown[0].detail.weightedCount").value(7.0))   // 1 + 1 + 5
                // 같은 점수의 두 후보는 소요 시간이 짧은 쪽이 추천
                .andExpect(jsonPath("$.routes[0].recommended").value(false))
                .andExpect(jsonPath("$.routes[1].recommended").value(true));
    }

    @Test
    void responseDeclaresTheScoreAsADesignParameterAndOnlyAxisA() throws Exception {
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of(candidate(0, "일반", 800)));

        mvc.perform(get("/api/routes/safe" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreModel.kind").value("DESIGN_PARAMETER"))
                .andExpect(jsonPath("$.scoreModel.axes[0]").value("A"))
                .andExpect(jsonPath("$.scoreModel.axes.length()").value(1))
                .andExpect(jsonPath("$.scoreModel.note").value(containsString("위험도나 안전도가 아닙니다")))
                .andExpect(jsonPath("$.scoreModel.note").value(containsString("아직 반영하지 않습니다")));
    }

    @Test
    void partialCandidatesStillRespond() throws Exception {
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of(candidate(4, "대로우선", 900)));

        mvc.perform(get("/api/routes/safe" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(1))
                .andExpect(jsonPath("$.routes[0].recommended").value(true));
    }

    @Test
    void allCandidatesFailingGivesGeneric502() throws Exception {
        Mockito.when(tmap.getCandidateRoutes(Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(List.of());

        mvc.perform(get("/api/routes/safe" + QUERY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("tmap"))))
                .andExpect(content().string(not(containsString("appKey"))));
    }

    @Test
    void coordinatesOutsideKoreaAreRejected() throws Exception {
        mvc.perform(get("/api/routes/safe?startLat=10&startLng=127.10&endLat=37.41&endLng=127.10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        mvc.perform(get("/api/routes/safe?startLat=37.4&startLng=127.10&endLat=37.41&endLng=NaN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/routes/safe?startLat=37.4&startLng=127.10"))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(tmap);
    }
}
