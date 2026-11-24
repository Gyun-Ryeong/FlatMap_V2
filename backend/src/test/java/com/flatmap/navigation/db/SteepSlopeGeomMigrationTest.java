package com.flatmap.navigation.db;

import com.flatmap.navigation.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3(geom 을 쓰기 원본으로 전환)가 이미 데이터가 들어 있는 DB 에서도 좌표를 보존하는지 확인한다.
 * V2 까지만 적용한 상태에서 lat/lng 로 행을 넣고 V3 를 적용한 뒤 비교한다.
 * 끝나면 DB 를 최신 버전으로 되돌린다(다른 테스트 클래스가 같은 DB 를 쓴다).
 */
@IntegrationTest
class SteepSlopeGeomMigrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${spring.datasource.url}")
    String url;

    private Flyway flyway(String targetVersion) {
        var config = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (targetVersion != null) config.target(MigrationVersion.fromVersion(targetVersion));
        return config.load();
    }

    @AfterEach
    void restoreLatestSchema() {
        flyway(null).migrate();
        jdbc.update("DELETE FROM steep_slope_areas");
    }

    @Test
    void v3KeepsExistingCoordinatesAndNullCoordinateRows() {
        assertThat(url).as("개발 DB 보호").contains("_test");
        flyway(null).clean();
        flyway("2").migrate();

        // V2 까지는 latitude/longitude 가 원본이다.
        jdbc.update("INSERT INTO steep_slope_areas (name, latitude, longitude, grade, source, region_code) " +
                "VALUES ('with-coords', 37.4012, 127.1234, 12.5, 'GYEONGGI', '41131')");
        jdbc.update("INSERT INTO steep_slope_areas (name, grade, source) VALUES ('no-coords', 3.0, 'DISASTER')");

        flyway(null).migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, latitude, longitude, ST_Y(geom) AS gy, ST_X(geom) AS gx, ST_SRID(geom) AS srid, " +
                "grade, source, region_code FROM steep_slope_areas ORDER BY name");
        assertThat(rows).hasSize(2);

        Map<String, Object> noCoords = rows.get(0);
        assertThat(noCoords.get("name")).isEqualTo("no-coords");
        assertThat(noCoords.get("latitude")).isNull();
        assertThat(noCoords.get("longitude")).isNull();
        assertThat(noCoords.get("gy")).isNull();

        Map<String, Object> withCoords = rows.get(1);
        assertThat(withCoords.get("latitude")).isEqualTo(37.4012);
        assertThat(withCoords.get("longitude")).isEqualTo(127.1234);
        assertThat(withCoords.get("gy")).isEqualTo(37.4012);
        assertThat(withCoords.get("gx")).isEqualTo(127.1234);
        assertThat(withCoords.get("srid")).isEqualTo(4326);
        assertThat(withCoords.get("grade")).isEqualTo(12.5);
        assertThat(withCoords.get("source")).isEqualTo("GYEONGGI");
        assertThat(withCoords.get("region_code")).isEqualTo("41131");

        // 반경 인덱스(geography 표현식 GiST)는 V3 이후에도 그대로 있다.
        Integer idx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'steep_slope_areas' " +
                "AND indexname = 'idx_steep_slope_areas_geom_geog'", Integer.class);
        assertThat(idx).isEqualTo(1);
    }
}
