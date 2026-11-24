package com.flatmap.navigation.web;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지도 레이어(사고다발지·교통약자 보호구역·장애인복지시설·그늘막·노인복지관) 조회 계약.
 * 프론트 레이어가 이 응답 필드(id, name, latitude, longitude, type/address/phone)를 그대로 쓴다. 조회(GET)는 공개다.
 */
@IntegrationTest
class ZoneLayerEndpointTest {

    private static final String[] TABLES = {
            "accident_zones", "protected_zones", "welfare_facilities", "shade_shelters", "senior_centers"};

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        for (String t : TABLES) jdbc.update("DELETE FROM " + t);
    }

    @Test
    void emptyTablesGiveEmptyArraysNotErrors() throws Exception {
        for (String path : new String[]{"accident-zones", "protected-zones", "welfare-facilities", "shade-shelters",
                "senior-centers"}) {
            mvc.perform(get("/api/safety/" + path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    void accidentZonesCarryNameTypeAndCoordinates() throws Exception {
        jdbc.update("INSERT INTO accident_zones (name, type, region_code, latitude, longitude) " +
                "VALUES ('수진 사거리', 'PEDESTRIAN', '41131', 37.4380, 127.1370)");
        jdbc.update("INSERT INTO accident_zones (name, type, region_code, latitude, longitude) " +
                "VALUES ('야탑 교차로', 'ELDERLY', '41135', 37.4110, 127.1280)");

        mvc.perform(get("/api/safety/accident-zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[?(@.name=='수진 사거리')].latitude").value(37.4380))
                .andExpect(jsonPath("$[?(@.name=='수진 사거리')].longitude").value(127.1370))
                .andExpect(jsonPath("$[?(@.name=='수진 사거리')].type").value("PEDESTRIAN"));
        // 종류 필터(대소문자 무시)
        mvc.perform(get("/api/safety/accident-zones").param("type", "elderly"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("야탑 교차로"));
    }

    @Test
    void protectedZonesWelfareShelterAndSeniorLayersHaveTheFieldsTheMapUses() throws Exception {
        jdbc.update("INSERT INTO protected_zones (name, type, address, region_code, latitude, longitude) " +
                "VALUES ('수진초 보호구역', '어린이', '성남시 수정구', '41131', 37.44, 127.13)");
        jdbc.update("INSERT INTO welfare_facilities (name, type, address, phone, region_code, latitude, longitude) " +
                "VALUES ('분당장애인복지관', '복지관', '성남시 분당구', '031-000-0000', '41135', 37.38, 127.12)");
        jdbc.update("INSERT INTO shade_shelters (name, address, region_code, latitude, longitude) " +
                "VALUES ('서현역 그늘막', '성남시 분당구', '41135', 37.385, 127.123)");
        jdbc.update("INSERT INTO senior_centers (name, address, phone, region_code, latitude, longitude) " +
                "VALUES ('중원노인복지관', '성남시 중원구', '031-111-1111', '41133', 37.43, 127.15)");

        mvc.perform(get("/api/safety/protected-zones"))
                .andExpect(jsonPath("$[0].name").value("수진초 보호구역"))
                .andExpect(jsonPath("$[0].type").value("어린이"))
                .andExpect(jsonPath("$[0].address").value("성남시 수정구"))
                .andExpect(jsonPath("$[0].latitude").value(37.44));
        mvc.perform(get("/api/safety/welfare-facilities"))
                .andExpect(jsonPath("$[0].name").value("분당장애인복지관"))
                .andExpect(jsonPath("$[0].phone").value("031-000-0000"));
        mvc.perform(get("/api/safety/shade-shelters"))
                .andExpect(jsonPath("$[0].name").value("서현역 그늘막"))
                .andExpect(jsonPath("$[0].longitude").value(127.123));
        mvc.perform(get("/api/safety/senior-centers"))
                .andExpect(jsonPath("$[0].name").value("중원노인복지관"))
                .andExpect(jsonPath("$[0].phone").value("031-111-1111"));

        // 지역 코드 접두어 필터
        mvc.perform(get("/api/safety/senior-centers").param("regionCode", "41135"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/safety/senior-centers").param("regionCode", "411"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void layerResponsesNeverCarryAnyRiskGrade() throws Exception {
        jdbc.update("INSERT INTO accident_zones (name, type, region_code, latitude, longitude) " +
                "VALUES ('x', 'T', '41131', 37.4, 127.1)");
        mvc.perform(get("/api/safety/accident-zones"))
                .andExpect(jsonPath("$[0].riskLevel").doesNotExist())
                .andExpect(jsonPath("$[0].geom").doesNotExist());
    }
}
