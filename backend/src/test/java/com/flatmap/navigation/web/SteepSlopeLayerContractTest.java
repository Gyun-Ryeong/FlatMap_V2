package com.flatmap.navigation.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.dto.DetourRequest;
import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "붕괴위험 경고 레이어" API 계약: 등급(riskLevel) 없이 경사도(°)·위치·출처만 반환한다.
 */
@IntegrationTest
class SteepSlopeLayerContractTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM steep_slope_areas");
    }

    private void insert(String name, double lat, double lng, Double grade, String source, String regionCode) {
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, region_code, geom) " +
                        "VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))",
                name, grade, source, regionCode, lng, lat);
    }

    @Test
    void layerResponseHasGradeLocationAndSourceButNoRiskLevel() throws Exception {
        insert("A", 37.40, 127.12, 12.5, "GYEONGGI", "41131");

        mvc.perform(get("/api/steep-slope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].grade").value(12.5))          // 단위 각도(°)
                .andExpect(jsonPath("$[0].latitude").value(37.40))
                .andExpect(jsonPath("$[0].longitude").value(127.12))
                .andExpect(jsonPath("$[0].source").value("GYEONGGI"))
                .andExpect(jsonPath("$[0].riskLevel").doesNotExist());
    }

    @Test
    void unmeasuredGradeIsNullNotZero() throws Exception {
        insert("no-grade", 37.40, 127.12, null, "DISASTER", "41131");

        mvc.perform(get("/api/steep-slope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].grade").value((Object) null));
    }

    @Test
    void regionAndSourceFiltersStillWork() throws Exception {
        insert("sujeong", 37.45, 127.14, 5.0, "GYEONGGI", "41131");
        insert("bundang", 37.38, 127.12, 6.0, "DISASTER", "41135");

        mvc.perform(get("/api/steep-slope").param("regionCode", "41135"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("bundang"));
        mvc.perform(get("/api/steep-slope/source/gyeonggi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("sujeong"));
    }

    @Test
    void routeSectionJsonHasNoRiskLevelAndKeepsNullGrade() throws Exception {
        JsonNode json = objectMapper.valueToTree(new RiskSection(37.4, 127.1, "A", null, "GYEONGGI", 111.04, 4));

        assertThat(json.has("riskLevel")).isFalse();
        assertThat(json.get("grade").isNull()).isTrue();
        assertThat(json.get("source").asText()).isEqualTo("GYEONGGI");
        assertThat(json.get("lat").asDouble()).isEqualTo(37.4);
        assertThat(json.get("lng").asDouble()).isEqualTo(127.1);
        assertThat(json.get("distanceFromRoute").asDouble()).isEqualTo(111.0);
        assertThat(json.get("nearestRouteIdx").asInt()).isEqualTo(4);
    }

    @Test
    void routeResponseHasNoOverallRisk() {
        // 등급이 없는 구간에서 파생되는 종합 위험도는 의미가 없어 제거했다(항상 SAFE 로 읽히는 오해를 막는다).
        RouteResponse response = new RouteResponse(List.of(new RouteResponse.Coord(37.4, 127.1)), 100, 60);
        response.setRiskSections(List.of(new RiskSection(37.4, 127.1, "A", 12.5, "GYEONGGI", 10, 0)));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.has("overallRisk")).isFalse();
        assertThat(json.get("riskSections")).hasSize(1);
    }

    @Test
    void detourRequestStillAcceptsLegacyClientsThatSendRiskLevel() throws Exception {
        String body = "{\"originLng\":127.1,\"originLat\":37.4,\"destLng\":127.2,\"destLat\":37.5,\"option\":\"SAFE\"," +
                "\"avoidPoints\":[{\"lat\":37.45,\"lng\":127.15,\"riskLevel\":\"HIGH\",\"nearestRouteIdx\":7}]}";

        DetourRequest request = objectMapper.readValue(body, DetourRequest.class);

        assertThat(request.getAvoidPoints()).hasSize(1);
        assertThat(request.getAvoidPoints().get(0).getNearestRouteIdx()).isEqualTo(7);
    }
}
