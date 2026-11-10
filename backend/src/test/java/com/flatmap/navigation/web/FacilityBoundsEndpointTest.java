package com.flatmap.navigation.web;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/safety/facilities — 지도 영역 안의 시설(공개 조회). */
@IntegrationTest
class FacilityBoundsEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
    }

    private void add(String type, String name, double lat, double lng) {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')", type, name, lng, lat);
    }

    private static final String BOUNDS = "?minLat=37.40&minLng=127.10&maxLat=37.42&maxLng=127.12";

    @Test
    void returnsOnlyFacilitiesInsideTheBoundsWithoutAuthentication() throws Exception {
        add("CCTV", "in-cctv", 37.41, 127.11);
        add("POLICE", "in-police", 37.415, 127.115);
        add("CCTV", "out-cctv", 37.50, 127.11);   // 위도 범위 밖
        add("CCTV", "out-cctv-lng", 37.41, 127.20); // 경도 범위 밖

        mvc.perform(get("/api/safety/facilities" + BOUNDS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.facilities[*].name", containsInAnyOrder("in-cctv", "in-police")))
                .andExpect(jsonPath("$.facilities[0].facilityType").isString())
                .andExpect(jsonPath("$.facilities[0].latitude").isNumber())
                .andExpect(jsonPath("$.facilities[0].longitude").isNumber());
    }

    @Test
    void typeParameterFiltersAndAcceptsSeveralTypes() throws Exception {
        add("CCTV", "c", 37.41, 127.11);
        add("SECURITY_LIGHT", "l", 37.411, 127.111);
        add("POLICE", "p", 37.412, 127.112);

        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&type=police"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilities[*].name", containsInAnyOrder("p")));

        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&type=CCTV,SECURITY_LIGHT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilities[*].name", containsInAnyOrder("c", "l")))
                .andExpect(jsonPath("$.facilities[*].name", not(hasItem("p"))));
    }

    @Test
    void marksTruncatedWhenMoreThanLimitExist() throws Exception {
        for (int i = 0; i < 5; i++) add("CCTV", "c" + i, 37.41 + i * 0.001, 127.11);

        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&limit=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.truncated").value(true));

        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&limit=5"))
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void rejectsInvalidBoundsLimitAndType() throws Exception {
        mvc.perform(get("/api/safety/facilities?minLat=37.42&minLng=127.10&maxLat=37.40&maxLng=127.12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOUNDS"));
        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&limit=2001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        mvc.perform(get("/api/safety/facilities" + BOUNDS + "&type=FIRE_STATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FACILITY_TYPE"));
        mvc.perform(get("/api/safety/facilities?minLat=37.40"))
                .andExpect(status().isBadRequest());
    }
}
