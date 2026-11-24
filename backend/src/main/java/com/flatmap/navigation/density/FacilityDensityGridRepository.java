package com.flatmap.navigation.density;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * facility_density_grid 접근. 공간 데이터이므로 JdbcTemplate + PostGIS 를 쓴다(CLAUDE.md 규칙 3).
 * 집계는 시설을 격자와 JOIN 해 한 번의 쿼리로 끝낸다 — 격자마다 묻지 않는다(N+1 금지).
 */
@Repository
public class FacilityDensityGridRepository {

    /**
     * 셀별 시설 종류 개수. 시설 좌표에서 셀 인덱스를 구해 GROUP BY 한 뒤 격자 셀 전체와 JOIN 한다.
     * <b>자기 자신과 인접 8칸에 시설이 하나도 없는 셀은 결과에 없다</b>(저장하지 않는다).
     * 셀은 반개구간 [하한, 상한) 이라 경계 위의 시설이 두 셀에 중복되지 않는다.
     */
    private static final String AGGREGATE_SQL =
            "WITH counts AS ( " +
            "  SELECT floor((ST_X(location)::numeric - ?::numeric) / ?::numeric)::int AS gi, " +
            "         floor((ST_Y(location)::numeric - ?::numeric) / ?::numeric)::int AS gj, " +
            "         COUNT(*) FILTER (WHERE facility_type = 'CCTV')           AS cctv, " +
            "         COUNT(*) FILTER (WHERE facility_type = 'SECURITY_LIGHT') AS light, " +
            "         COUNT(*) FILTER (WHERE facility_type = 'POLICE')         AS police " +
            "  FROM safety_facility GROUP BY 1, 2 " +
            "), in_grid AS ( " +
            "  SELECT * FROM counts WHERE gi >= 0 AND gi < ?::int AND gj >= 0 AND gj < ?::int " +
            "), cells AS ( " +
            "  SELECT gi, gj FROM generate_series(0, ?::int - 1) gi CROSS JOIN generate_series(0, ?::int - 1) gj " +
            ") " +
            "SELECT c.gi, c.gj, COALESCE(o.cctv, 0), COALESCE(o.light, 0), COALESCE(o.police, 0) " +
            "FROM cells c LEFT JOIN in_grid o ON o.gi = c.gi AND o.gj = c.gj " +
            "WHERE EXISTS (SELECT 1 FROM in_grid n " +
            "              WHERE n.gi BETWEEN c.gi - 1 AND c.gi + 1 AND n.gj BETWEEN c.gj - 1 AND c.gj + 1) " +
            "ORDER BY c.gi, c.gj";

    private static final String INSERT_SQL =
            "INSERT INTO facility_density_grid (grid_i, grid_j, grid_polygon, facility_count, cctv_count, " +
            "light_count, police_count, density_score, density_level, score_version) " +
            "VALUES (?, ?, ST_MakeEnvelope(?, ?, ?, ?, 4326), ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_SQL =
            "SELECT grid_i, grid_j, ST_YMin(grid_polygon) AS min_lat, ST_XMin(grid_polygon) AS min_lng, " +
            "ST_YMax(grid_polygon) AS max_lat, ST_XMax(grid_polygon) AS max_lng, " +
            "cctv_count, light_count, police_count, density_score, density_level, score_version, calculated_at " +
            "FROM facility_density_grid ";

    private final JdbcTemplate jdbc;

    public FacilityDensityGridRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 재계산이 겹치지 않도록 테이블 쓰기를 잠근다(읽기는 막지 않는다). 트랜잭션 안에서만 의미가 있다. */
    public void lockForRewrite() {
        jdbc.execute("LOCK TABLE facility_density_grid IN EXCLUSIVE MODE");
    }

    public List<GridCellCounts> aggregateCells(GridSpec spec) {
        return jdbc.query(AGGREGATE_SQL,
                (rs, n) -> new GridCellCounts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
                spec.minLng(), spec.cellLng(), spec.minLat(), spec.cellLat(),
                spec.cols(), spec.rows(), spec.cols(), spec.rows());
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM facility_density_grid");
    }

    /** 셀 인덱스에서 폴리곤을 만들어 한 번에(배치) 넣는다. */
    public void insertAll(List<ScoredCell> cells, GridSpec spec, int scoreVersion) {
        if (cells.isEmpty()) return;
        jdbc.batchUpdate(INSERT_SQL, cells, 500, (ps, cell) -> {
            double[] b = spec.cellBounds(cell.counts().i(), cell.counts().j());
            GridCellCounts c = cell.counts();
            ps.setInt(1, c.i());
            ps.setInt(2, c.j());
            ps.setDouble(3, b[0]);
            ps.setDouble(4, b[1]);
            ps.setDouble(5, b[2]);
            ps.setDouble(6, b[3]);
            ps.setInt(7, c.cctv() + c.light() + c.police());
            ps.setInt(8, c.cctv());
            ps.setInt(9, c.light());
            ps.setInt(10, c.police());
            ps.setBigDecimal(11, cell.score());
            ps.setString(12, cell.level().name());
            ps.setInt(13, scoreVersion);
        });
    }

    /** 전체 셀. */
    public List<DensityCell> findAll() {
        return jdbc.query(SELECT_SQL + "ORDER BY grid_i, grid_j", (rs, n) -> map(rs));
    }

    /** 사각형과 겹치는 셀. */
    public List<DensityCell> findIntersecting(double minLat, double minLng, double maxLat, double maxLng) {
        return jdbc.query(SELECT_SQL + "WHERE ST_Intersects(grid_polygon, ST_MakeEnvelope(?, ?, ?, ?, 4326)) " +
                        "ORDER BY grid_i, grid_j",
                (rs, n) -> map(rs), minLng, minLat, maxLng, maxLat);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM facility_density_grid", Long.class);
        return n == null ? 0 : n;
    }

    private static DensityCell map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp calculated = rs.getTimestamp("calculated_at");
        int cctv = rs.getInt("cctv_count");
        int light = rs.getInt("light_count");
        int police = rs.getInt("police_count");
        return DensityCell.of(rs.getInt("grid_i"), rs.getInt("grid_j"),
                rs.getDouble("min_lat"), rs.getDouble("min_lng"), rs.getDouble("max_lat"), rs.getDouble("max_lng"),
                cctv, light, police, rs.getBigDecimal("density_score"),
                DensityLevel.valueOf(rs.getString("density_level")), rs.getInt("score_version"),
                calculated == null ? null : calculated.toLocalDateTime());
    }

    /** 점수가 매겨진 저장 대상 셀. */
    public record ScoredCell(GridCellCounts counts, java.math.BigDecimal score, DensityLevel level) {
    }
}
