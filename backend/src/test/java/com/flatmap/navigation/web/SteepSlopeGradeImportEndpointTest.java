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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 번들 CSV 적재를 실제 PostGIS 테스트 DB 로 확인한다. */
@IntegrationTest
class SteepSlopeGradeImportEndpointTest {

    private static final String PATH = "/api/steep-slope/import/seongnam-grades";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${admin.api-key}")
    String adminApiKey;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM steep_slope_areas");
    }

    private void callImport() throws Exception {
        mvc.perform(post(PATH).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed").value(84))
                .andExpect(jsonPath("$.saved").value(84))
                .andExpect(jsonPath("$.withGrade").value(84));
    }

    @Test
    void loadsAll84RowsWithGradeAndGeometry() throws Exception {
        callImport();

        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas WHERE source = 'MOIS_CSV'")).isEqualTo(84);
        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas WHERE grade IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas WHERE geom IS NULL")).isZero();
        Double taepyeong = jdbc.queryForObject(
                "SELECT grade FROM steep_slope_areas WHERE name = '경기 성남 수정 태평 N2지구'", Double.class);
        assertThat(taepyeong).isEqualTo(12.05);
        // 위치는 geom(WGS84)에서 파생된 값이다.
        Double lat = jdbc.queryForObject(
                "SELECT latitude FROM steep_slope_areas WHERE name = '경기 성남 수정 태평 N2지구'", Double.class);
        assertThat(lat).isBetween(37.45, 37.452);
    }

    @Test
    void runningTwiceGivesTheSameResultAndReplacesRatherThanDuplicates() throws Exception {
        callImport();
        callImport();

        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas")).isEqualTo(84);
    }

    @Test
    void replacesOldMoisRowsButLeavesOtherSourcesAlone() throws Exception {
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) VALUES " +
                "('옛 행', NULL, 'MOIS_CSV', ST_SetSRID(ST_MakePoint(127.1, 37.4), 4326))");
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) VALUES " +
                "('다른 출처', 7.5, 'DISASTER', ST_SetSRID(ST_MakePoint(127.1, 37.4), 4326))");

        callImport();

        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas WHERE name = '옛 행'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas WHERE name = '다른 출처' AND source = 'DISASTER'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas")).isEqualTo(85);
    }

    @Test
    void requiresAdminKey() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        assertThat(count("SELECT COUNT(*) FROM steep_slope_areas")).isZero();
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
