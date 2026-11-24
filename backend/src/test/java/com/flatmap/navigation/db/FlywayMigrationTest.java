package com.flatmap.navigation.db;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V1__baseline.sql 이 확정 결정을 그대로 반영하는지 확인한다. */
@IntegrationTest
class FlywayMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1IsAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).contains("1");
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    void postgisIsEnabled() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void unifiedTablesExist() {
        for (String t : List.of("safety_facility", "facility_density_grid", "steep_slope_areas",
                "accident_zones", "protected_zones", "welfare_facilities", "shade_shelters", "senior_centers",
                "users", "favorites")) {
            assertThat(tableExists(t)).as("테이블 " + t).isTrue();
        }
    }

    @Test
    void droppedOrExcludedTablesAreNotCreated() {
        // 확정 결정: 만들지 않는다 / 이름이 바뀌었다 / 통합되었다
        for (String t : List.of("accident_records", "route_segments", "road_segments", "routes",
                "danger_zone", "cctv_locations", "security_lights")) {
            assertThat(tableExists(t)).as("테이블 " + t + " 은 없어야 한다").isFalse();
        }
    }

    @Test
    void baselineContainsNoData() {
        for (String t : List.of("safety_facility", "facility_density_grid", "steep_slope_areas",
                "accident_zones", "protected_zones", "welfare_facilities", "shade_shelters", "senior_centers")) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class);
            assertThat(n).as(t + " 행 수").isZero();
        }
    }

    @Test
    void steepSlopeAreasHasNoRiskLevelButHasGradeAndGeometry() {
        List<String> cols = columns("steep_slope_areas");
        assertThat(cols).doesNotContain("risk_level");
        assertThat(cols).contains("grade", "geom");
        assertThat(jdbc.queryForObject(
                "SELECT udt_name FROM information_schema.columns WHERE table_name='steep_slope_areas' AND column_name='geom'",
                String.class)).isEqualTo("geometry");
    }

    @Test
    void steepSlopeLatLngAreDerivedFromGeomAndGradeIsDegrees() {
        // geom 이 쓰기 원본이고 latitude/longitude 는 거기서 파생된 읽기 전용 컬럼이다(V3).
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source, geom) " +
                "VALUES ('t', 12.5, 'TEST', ST_SetSRID(ST_MakePoint(127.12, 37.40), 4326))");
        try {
            Double lat = jdbc.queryForObject("SELECT latitude FROM steep_slope_areas WHERE name='t'", Double.class);
            Double lng = jdbc.queryForObject("SELECT longitude FROM steep_slope_areas WHERE name='t'", Double.class);
            assertThat(lat).isEqualTo(37.40);
            assertThat(lng).isEqualTo(127.12);
            // latitude/longitude 는 생성 컬럼이라 직접 쓸 수 없다.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO steep_slope_areas (name, latitude, longitude) " +
                    "VALUES ('direct', 37.40, 127.12)")).isInstanceOf(BadSqlGrammarException.class);
            // 단위는 각도(°). 90 이상은 %를 넣은 실수로 보고 거부한다.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO steep_slope_areas (name, grade, geom) " +
                    "VALUES ('bad', 95, ST_SetSRID(ST_MakePoint(127.12, 37.40), 4326))"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM steep_slope_areas");
        }
    }

    @Test
    void facilityDensityGridUsesDensityLevelsNotDangerWording() {
        List<String> cols = columns("facility_density_grid");
        assertThat(cols).contains("density_level", "density_score", "facility_count", "grid_polygon");
        assertThat(cols).doesNotContain("risk_level", "safety_score");

        String insert = "INSERT INTO facility_density_grid (grid_i, grid_j, grid_polygon, facility_count, cctv_count, density_score, density_level) " +
                "VALUES (?, ?, ST_MakeEnvelope(127.04, 37.33, 127.0434, 37.3327, 4326), 3, 3, 47, ?)";
        try {
            int i = 0;
            for (String level : List.of("LOW_DENSITY", "MEDIUM", "HIGH_DENSITY")) {
                jdbc.update(insert, i++, 0, level);
            }
            assertThatThrownBy(() -> jdbc.update(insert, 10, 10, "HIGH"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, 11, 11, "VERY_HIGH"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM facility_density_grid");
        }
    }

    @Test
    void spatialIndexesAreGistAndGeographyExpressionIndexesExist() {
        List<String> defs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'", String.class);
        for (String table : List.of("safety_facility", "facility_density_grid", "accident_zones",
                "protected_zones", "welfare_facilities", "shade_shelters", "senior_centers", "steep_slope_areas")) {
            assertThat(defs).as(table + " 에 geography 표현식 GiST 인덱스")
                    .anyMatch(d -> d.contains(" ON public." + table + " ")
                            && d.toLowerCase().contains("using gist")
                            && d.toLowerCase().contains("geography"));
        }
        // 사각형/교차 조회용 geometry GiST (시설, 격자)
        assertThat(defs).anyMatch(d -> d.contains("ON public.safety_facility ")
                && d.toLowerCase().contains("using gist (location)"));
        assertThat(defs).anyMatch(d -> d.contains("ON public.facility_density_grid ")
                && d.toLowerCase().contains("using gist (grid_polygon)"));
    }

    private boolean tableExists(String name) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ?",
                Integer.class, name);
        return n != null && n > 0;
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name = ?",
                String.class, table);
    }
}
