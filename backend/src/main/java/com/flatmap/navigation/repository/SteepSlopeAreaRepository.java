package com.flatmap.navigation.repository;

import com.flatmap.navigation.model.SteepSlopeArea;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * steep_slope_areas 접근. 공간 데이터이므로 JdbcTemplate + PostGIS 를 쓴다(CLAUDE.md 규칙 3).
 *
 * <ul>
 *   <li>위치는 geom(Point, 4326)이 원본이다. 쓰기는 {@code ST_SetSRID(ST_MakePoint(lng, lat), 4326)} 로만 한다.
 *       latitude/longitude 는 geom 에서 파생되는 읽기 전용 컬럼이다(V3 마이그레이션).</li>
 *   <li>반경은 {@code ST_DWithin(geography, 미터)} 로 DB 가 계산한다. 애플리케이션에서 haversine 을 돌지 않는다(규칙 4).</li>
 * </ul>
 */
@Repository
public class SteepSlopeAreaRepository {

    /** 경로 좌표 중 이 급경사지에 가장 가까운 정점의 인덱스(0 기준)와 거리(미터). */
    public record RouteHit(SteepSlopeArea area, double distanceMeters, int nearestIdx) {}

    private static final String COLUMNS =
            "id, name, latitude, longitude, grade, source, region_code, sigungu, eupmyeon, jibun, is_mountain, created_at";

    private static final String SELECT = "SELECT " + COLUMNS + " FROM steep_slope_areas ";

    private static final String INSERT =
            "INSERT INTO steep_slope_areas " +
            "(name, grade, source, region_code, sigungu, eupmyeon, jibun, is_mountain, created_at, geom) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))";

    /** 경로 좌표를 점 집합으로 풀고, 반경 안에 든 급경사지마다 가장 가까운 경로 정점을 찾는다. */
    private static final String ALONG_ROUTE =
            "WITH route AS (" +
            "  SELECT t.idx, ST_SetSRID(ST_MakePoint(t.lng, t.lat), 4326)::geography AS g " +
            "  FROM unnest(?::float8[], ?::float8[]) WITH ORDINALITY AS t(lat, lng, idx)" +
            "), bounds AS (" +
            "  SELECT ST_Collect(g::geometry)::geography AS g FROM route" +
            ") " +
            "SELECT a.id, a.name, a.latitude, a.longitude, a.grade, a.source, a.region_code, a.sigungu, " +
            "       a.eupmyeon, a.jibun, a.is_mountain, a.created_at, n.idx - 1 AS nearest_idx, n.dist " +
            "FROM bounds b " +
            "JOIN steep_slope_areas a ON ST_DWithin(a.geom::geography, b.g, ?) " +
            "CROSS JOIN LATERAL (" +
            "  SELECT r.idx, ST_Distance(a.geom::geography, r.g) AS dist FROM route r ORDER BY dist, r.idx LIMIT 1" +
            ") n " +
            "WHERE n.dist <= ? " +
            "ORDER BY n.idx, a.id";

    private final JdbcTemplate jdbc;

    private final RowMapper<SteepSlopeArea> rowMapper = (rs, i) -> {
        SteepSlopeArea a = new SteepSlopeArea();
        a.setId(rs.getLong("id"));
        a.setName(rs.getString("name"));
        a.setLatitude(nullableDouble(rs, "latitude"));
        a.setLongitude(nullableDouble(rs, "longitude"));
        a.setGrade(nullableDouble(rs, "grade"));
        a.setSource(rs.getString("source"));
        a.setRegionCode(rs.getString("region_code"));
        a.setSigungu(rs.getString("sigungu"));
        a.setEupmyeon(rs.getString("eupmyeon"));
        a.setJibun(rs.getString("jibun"));
        a.setIsMountain(rs.getBoolean("is_mountain"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) a.setCreatedAt(created.toLocalDateTime());
        return a;
    };

    public SteepSlopeAreaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----- 조회 -----

    public List<SteepSlopeArea> findAll() {
        return jdbc.query(SELECT + "ORDER BY id", rowMapper);
    }

    public Optional<SteepSlopeArea> findById(long id) {
        return jdbc.query(SELECT + "WHERE id = ?", rowMapper, id).stream().findFirst();
    }

    public List<SteepSlopeArea> findBySource(String source) {
        return jdbc.query(SELECT + "WHERE source = ? ORDER BY id", rowMapper, source);
    }

    public List<SteepSlopeArea> findByRegionCodeStartingWith(String regionCodePrefix) {
        return jdbc.query(SELECT + "WHERE region_code LIKE ? ORDER BY id", rowMapper,
                escapeLike(regionCodePrefix) + "%");
    }

    public long count() {
        return queryCount("SELECT COUNT(*) FROM steep_slope_areas");
    }

    public long countBySource(String source) {
        return queryCount("SELECT COUNT(*) FROM steep_slope_areas WHERE source = ?", source);
    }

    public long countWithGrade() {
        return queryCount("SELECT COUNT(*) FROM steep_slope_areas WHERE grade IS NOT NULL");
    }

    // ----- 공간 조회 -----

    /**
     * 경로 좌표({@code [lat, lng]} 목록)에서 반경(미터) 이내에 있는 급경사지를 한 번의 쿼리로 찾는다.
     * 거리는 경로 <b>정점</b>까지의 최소 거리이고, {@code nearestIdx} 는 그 정점의 인덱스(0 기준, 동률이면 앞쪽)다.
     * 결과는 경로를 따라(nearestIdx 오름차순, 같으면 id 순) 정렬된다. 좌표가 없는 급경사지는 제외된다.
     */
    public List<RouteHit> findAlongRoute(List<double[]> routePoints, double meters) {
        if (routePoints == null || routePoints.isEmpty()) return List.of();
        Double[] lats = new Double[routePoints.size()];
        Double[] lngs = new Double[routePoints.size()];
        for (int i = 0; i < routePoints.size(); i++) {
            lats[i] = routePoints.get(i)[0];
            lngs[i] = routePoints.get(i)[1];
        }
        return jdbc.query(ALONG_ROUTE, ps -> {
            ps.setArray(1, float8Array(ps.getConnection(), lats));
            ps.setArray(2, float8Array(ps.getConnection(), lngs));
            ps.setDouble(3, meters);
            ps.setDouble(4, meters);
        }, (rs, i) -> new RouteHit(rowMapper.mapRow(rs, i), rs.getDouble("dist"), rs.getInt("nearest_idx")));
    }

    /**
     * 주어진 점들({@code [lat, lng]}) 중 반경(미터) 이내에 급경사지가 하나라도 있는 점의 인덱스(0 기준) 집합.
     * 점 목록 전체를 한 번의 쿼리로 판정한다.
     */
    public Set<Integer> findPointIndexesWithAreaWithin(List<double[]> points, double meters) {
        if (points == null || points.isEmpty()) return Set.of();
        Double[] lats = new Double[points.size()];
        Double[] lngs = new Double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            lats[i] = points.get(i)[0];
            lngs[i] = points.get(i)[1];
        }
        List<Integer> idx = jdbc.query(
                "SELECT t.idx - 1 FROM unnest(?::float8[], ?::float8[]) WITH ORDINALITY AS t(lat, lng, idx) " +
                "WHERE EXISTS (SELECT 1 FROM steep_slope_areas a WHERE ST_DWithin(" +
                "a.geom::geography, ST_SetSRID(ST_MakePoint(t.lng, t.lat), 4326)::geography, ?))",
                ps -> {
                    ps.setArray(1, float8Array(ps.getConnection(), lats));
                    ps.setArray(2, float8Array(ps.getConnection(), lngs));
                    ps.setDouble(3, meters);
                },
                (rs, i) -> rs.getInt(1));
        return new HashSet<>(idx);
    }

    // ----- 쓰기 -----

    /** 행을 삽입한다. 위치는 geom 으로 쓴다(좌표가 null 이면 geom 도 null). @return 삽입된 행 수 */
    @Transactional
    public int insertAll(List<SteepSlopeArea> areas) {
        if (areas.isEmpty()) return 0;
        int[][] results = jdbc.batchUpdate(INSERT, areas, 500, (ps, a) -> {
            ps.setString(1, a.getName());
            ps.setObject(2, a.getGrade(), Types.DOUBLE);
            ps.setString(3, a.getSource());
            ps.setString(4, a.getRegionCode());
            ps.setString(5, a.getSigungu());
            ps.setString(6, a.getEupmyeon());
            ps.setString(7, a.getJibun());
            ps.setBoolean(8, Boolean.TRUE.equals(a.getIsMountain()));
            ps.setTimestamp(9, Timestamp.valueOf(a.getCreatedAt() != null
                    ? a.getCreatedAt() : java.time.LocalDateTime.now()));
            ps.setObject(10, a.getLongitude(), Types.DOUBLE);
            ps.setObject(11, a.getLatitude(), Types.DOUBLE);
        });
        int inserted = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                inserted += r > 0 ? r : (r == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0);
            }
        }
        return inserted;
    }

    /** 한 출처(source)의 행을 지우고 새로 적재한다(한 트랜잭션). @return 삽입된 행 수 */
    @Transactional
    public int replaceSource(String source, List<SteepSlopeArea> areas) {
        deleteBySource(source);
        return insertAll(areas);
    }

    public int deleteBySource(String source) {
        return jdbc.update("DELETE FROM steep_slope_areas WHERE source = ?", source);
    }

    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM steep_slope_areas WHERE id = ?", id) > 0;
    }

    /** 경사도(°)를 바꾼다. grade 가 null 이면 값을 비운다. @return 갱신 여부 */
    public boolean updateGrade(long id, Double grade) {
        return jdbc.update("UPDATE steep_slope_areas SET grade = ? WHERE id = ?", grade, id) > 0;
    }

    /** id → 경사도(°) 일괄 갱신. 존재하지 않는 id 는 건너뛴다. @return 실제 갱신된 행 수 */
    @Transactional
    public int updateGrades(Map<Long, Double> gradesById) {
        if (gradesById.isEmpty()) return 0;
        List<Map.Entry<Long, Double>> entries = List.copyOf(gradesById.entrySet());
        int[][] results = jdbc.batchUpdate("UPDATE steep_slope_areas SET grade = ? WHERE id = ?", entries, 500,
                (ps, e) -> {
                    ps.setObject(1, e.getValue(), Types.DOUBLE);
                    ps.setLong(2, e.getKey());
                });
        int updated = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                updated += r > 0 ? r : (r == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0);
            }
        }
        return updated;
    }

    /**
     * 동기화(MOIS_CSV)용 갱신: 이름·행정구역·지번·위치를 덮어쓴다. 경사도(grade)는 건드리지 않는다.
     * isMountain 이 null 이면 기존 값을 유지한다.
     *
     * @return 갱신 여부
     */
    public boolean updateDescriptive(long id, SteepSlopeArea a) {
        return jdbc.update(
                "UPDATE steep_slope_areas SET name = ?, sigungu = ?, eupmyeon = ?, jibun = ?, region_code = ?, " +
                "is_mountain = COALESCE(?, is_mountain), geom = ST_SetSRID(ST_MakePoint(?, ?), 4326) WHERE id = ?",
                ps -> {
                    ps.setString(1, a.getName());
                    ps.setString(2, a.getSigungu());
                    ps.setString(3, a.getEupmyeon());
                    ps.setString(4, a.getJibun());
                    ps.setString(5, a.getRegionCode());
                    ps.setObject(6, a.getIsMountain(), Types.BOOLEAN);
                    ps.setObject(7, a.getLongitude(), Types.DOUBLE);
                    ps.setObject(8, a.getLatitude(), Types.DOUBLE);
                    ps.setLong(9, id);
                }) > 0;
    }

    // ----- 내부 -----

    private long queryCount(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private static Array float8Array(java.sql.Connection con, Double[] values) throws java.sql.SQLException {
        return con.createArrayOf("float8", values);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
