package com.flatmap.navigation.web;

import com.flatmap.navigation.config.AdminApiKeyFilter;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DensityGridEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Value("${admin.api-key}")
    String adminApiKey;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
        jdbc.update("DELETE FROM facility_density_grid");
    }

    private void recalc() throws Exception {
        mvc.perform(post("/api/density-grid/recalculate").header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk());
    }

    private void addCctv(double lat, double lng) {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES ('CCTV', 'c', ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')", lng, lat);
    }

    @Test
    void recalculateRequiresTheAdminKey() throws Exception {
        mvc.perform(post("/api/density-grid/recalculate")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/density-grid/recalculate").header(AdminApiKeyFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recalculateReportsCellsByLevelAndScoreVersion() throws Exception {
        addCctv(37.3855, 127.0765);
        mvc.perform(post("/api/density-grid/recalculate").header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCells").value(9))
                .andExpect(jsonPath("$.byLevel.LOW_DENSITY").value(9))
                .andExpect(jsonPath("$.byLevel.MEDIUM").value(0))
                .andExpect(jsonPath("$.byLevel.HIGH_DENSITY").value(0))
                .andExpect(jsonPath("$.scoreVersion").value(1));
    }

    @Test
    void getIsPublicAndReturnsCellsWithNoteAndNoDangerWording() throws Exception {
        addCctv(37.3855, 127.0765);
        recalc();

        mvc.perform(get("/api/density-grid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(9))
                .andExpect(jsonPath("$.note").value(containsString("위험하다는 뜻이 아니라")))
                .andExpect(jsonPath("$.cells.length()").value(9))
                .andExpect(jsonPath("$.cells[0].gridI").isNumber())
                .andExpect(jsonPath("$.cells[0].densityLevel").isString())
                .andExpect(jsonPath("$.cells[0].densityScore").isNumber())
                .andExpect(jsonPath("$.cells[0].facilityCount").isNumber())
                .andExpect(jsonPath("$.cells[0].cctvCount").isNumber())
                .andExpect(jsonPath("$.cells[0].bounds.length()").value(5))
                .andExpect(content().string(not(containsString("riskLevel"))))
                .andExpect(content().string(not(containsString("dangerZone"))));
    }

    @Test
    void emptyGridIsAnEmptyList() throws Exception {
        mvc.perform(get("/api/density-grid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.cells.length()").value(0));
    }

    @Test
    void boundsFilterReturnsOnlyOverlappingCells() throws Exception {
        addCctv(37.3855, 127.0765);     // 셀 (10,20) 과 인접 8칸
        addCctv(37.5000, 127.1800);     // 멀리 떨어진 셀
        recalc();

        // 첫 번째 시설 주변만: 셀 (10,20) 중앙 근처의 아주 작은 사각형은 한 셀과만 겹친다
        mvc.perform(get("/api/density-grid?minLat=37.3855&minLng=127.0765&maxLat=37.3856&maxLng=127.0766"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.cells[0].gridI").value(10))
                .andExpect(jsonPath("$.cells[0].gridJ").value(20));
    }

    @Test
    void incompleteOrInvertedBoundsAreRejected() throws Exception {
        mvc.perform(get("/api/density-grid?minLat=37.3&minLng=127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOUNDS"));
        mvc.perform(get("/api/density-grid?minLat=37.5&minLng=127.0&maxLat=37.4&maxLng=127.1"))
                .andExpect(status().isBadRequest());
    }
}
