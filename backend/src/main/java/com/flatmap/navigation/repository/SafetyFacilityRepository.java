package com.flatmap.navigation.repository;

import com.flatmap.navigation.model.SafetyFacility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * safety_facility 접근. 공간 데이터이므로 JdbcTemplate + PostGIS 를 쓴다(CLAUDE.md 규칙 3).
 * 반경 계산은 DB 의 ST_DWithin(geography, 미터)로 한다 — 애플리케이션에서 haversine 을 돌지 않는다(규칙 4).
 */
@Repository
public class SafetyFacilityRepository {

    private static final String SELECT =
            "SELECT id, facility_type, name, address, region_code, data_source, created_at, " +
            "ST_Y(location) AS latitude, ST_X(location) AS longitude FROM safety_facility ";

    private static final String INSERT_ON_CONFLICT =
            "INSERT INTO safety_facility (facility_type, name, address, region_code, location, data_source) " +
            "VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?) " +
            "ON CONFLICT (facility_type, location) DO NOTHING";

    private final JdbcTemplate jdbc;

    private final RowMapper<SafetyFacility> rowMapper = (rs, i) -> {
        SafetyFacility f = new SafetyFacility();
        f.setId(rs.getLong("id"));
        f.setFacilityType(rs.getString("facility_type"));
        f.setName(rs.getString("name"));
        f.setAddress(rs.getString("address"));
        f.setRegionCode(rs.getString("region_code"));
        f.setDataSource(rs.getString("data_source"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) f.setCreatedAt(created.toLocalDateTime());
        f.setLatitude(rs.getDouble("latitude"));
        f.setLongitude(rs.getDouble("longitude"));
        return f;
    };

    public SafetyFacilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SafetyFacility> findByType(String facilityType) {
        return jdbc.query(SELECT + "WHERE facility_type = ? ORDER BY id", rowMapper, facilityType);
    }

    public List<SafetyFacility> findByTypeAndRegionCodePrefix(String facilityType, String regionCodePrefix) {
        return jdbc.query(SELECT + "WHERE facility_type = ? AND region_code LIKE ? ORDER BY id",
                rowMapper, facilityType, escapeLike(regionCodePrefix) + "%");
    }

    public long countByType(String facilityType) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility WHERE facility_type = ?",
                Long.class, facilityType);
        return n == null ? 0 : n;
    }

    /**
     * 중심점 반경(미터) 이내 시설. location::geography 표현식 GiST 인덱스를 탄다.
     */
    public List<SafetyFacility> findWithinMeters(String facilityType, double lat, double lng, double meters) {
        return jdbc.query(SELECT + "WHERE facility_type = ? AND ST_DWithin(" +
                        "location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?) ORDER BY id",
                rowMapper, facilityType, lng, lat, meters);
    }

    public long countWithinMeters(String facilityType, double lat, double lng, double meters) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility WHERE facility_type = ? AND ST_DWithin(" +
                        "location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)",
                Long.class, facilityType, lng, lat, meters);
        return n == null ? 0 : n;
    }

    public long countByDataSource(String dataSource) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility WHERE data_source = ?",
                Long.class, dataSource);
        return n == null ? 0 : n;
    }

    /**
     * 한 출처(data_source)의 전체 행 수와 "나쁜 값" 행 수 — {전체, 나쁜 행}.
     * 나쁜 값 기준은 TextQuality 와 같다: NULL/공백/'?'만/U+FFFD 포함. checkAddress 가 false 면 name 만 본다.
     */
    public long[] badTextCounts(String dataSource, boolean checkAddress) {
        return jdbc.queryForObject(
                "SELECT COUNT(*), COUNT(*) FILTER (WHERE " +
                "name IS NULL OR name ~ '^\\s*\\?*\\s*$' OR position(chr(65533) in name) > 0 " +
                "OR (?::boolean AND (address IS NULL OR address ~ '^\\s*\\?*\\s*$' OR position(chr(65533) in address) > 0))) " +
                "FROM safety_facility WHERE data_source = ?",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, checkAddress, dataSource);
    }

    /**
     * 같은 (종류, 출처) 행을 지우고 새로 적재한다. 같은 (종류, 좌표)가 이미 있으면 건너뛴다(ON CONFLICT DO NOTHING).
     *
     * @return 실제로 삽입된 행 수
     */
    @Transactional
    public int replaceAll(String facilityType, String dataSource, List<SafetyFacility> facilities) {
        jdbc.update("DELETE FROM safety_facility WHERE facility_type = ? AND data_source = ?",
                facilityType, dataSource);
        return insertIgnoringDuplicates(facilities);
    }

    /**
     * 적재: INSERT ... ON CONFLICT (facility_type, location) DO NOTHING.
     *
     * @return 실제로 삽입된 행 수 (중복으로 건너뛴 행은 포함하지 않는다)
     */
    @Transactional
    public int insertIgnoringDuplicates(List<SafetyFacility> facilities) {
        if (facilities.isEmpty()) return 0;
        int[][] results = jdbc.batchUpdate(INSERT_ON_CONFLICT, facilities, 500, (ps, f) -> {
            ps.setString(1, f.getFacilityType());
            ps.setString(2, f.getName());
            ps.setString(3, f.getAddress());
            ps.setString(4, f.getRegionCode());
            ps.setDouble(5, f.getLongitude());
            ps.setDouble(6, f.getLatitude());
            ps.setString(7, f.getDataSource());
        });
        int inserted = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                if (r > 0) inserted += r;
            }
        }
        return inserted;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
