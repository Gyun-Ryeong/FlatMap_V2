package com.flatmap.navigation.walk;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * walk_slope_cell 접근(보행난이도 B축 전용 경사 격자). JdbcTemplate + PostGIS 를 쓴다(CLAUDE.md 규칙 3).
 * 급경사지 테이블(steep_slope_areas)과는 완전히 별개다.
 */
@Repository
public class WalkSlopeRepository {

    /** 한 번에 넣는 셀 수. unnest 배열 삽입이라 문장 하나가 이 크기의 배치를 처리한다. */
    private static final int INSERT_CHUNK = 50_000;

    private static final String INSERT_SQL =
            "INSERT INTO walk_slope_cell (cell_i, cell_j, slope_deg, elevation_m) " +
            "SELECT * FROM unnest(?::int[], ?::int[], ?::float8[], ?::float4[])";

    /**
     * 경로 전체를 한 번의 쿼리로 샘플링한다(샘플마다 묻지 않는다 — DECISIONS §2).
     * ① 경로(4326)를 미터 좌표계(EPSG:5186, 한국 중부원점)로 옮겨 ② 길이를 같은 간격으로 나눈 지점을 만들고
     * ③ 지점을 도로 되돌려 가장 가까운 셀 중심의 인덱스(PK)로 경사 격자와 LEFT JOIN 한다. 값이 없는 지점은 slope_deg 가 NULL 이다.
     * 샘플 수 = ⌈길이 / 간격⌉ + 1 (상한 있음). 경로 길이는 geography(WGS84 타원체)로 잰다.
     */
    private static final String SAMPLE_SQL =
            "WITH src AS (SELECT ST_GeomFromText(?, 4326) AS g), " +
            "meta AS (SELECT ST_Transform(g, 5186) AS m, ST_Length(g::geography) AS len_m FROM src), " +
            "plan AS (SELECT m, len_m, LEAST(?::int, GREATEST(1, CEIL(len_m / ?::float8)::int)) AS n FROM meta), " +
            "samples AS (" +
            "  SELECT k, ST_Transform(ST_LineInterpolatePoint(m, k::float8 / n), 4326) AS p, len_m " +
            "  FROM plan, generate_series(0, plan.n) AS k" +
            ") " +
            "SELECT s.k, c.slope_deg, s.len_m " +
            "FROM samples s " +
            "LEFT JOIN walk_slope_cell c " +
            "  ON c.cell_i = floor((ST_X(s.p)::numeric - ?::numeric) * ?::numeric + 0.5)::int " +
            " AND c.cell_j = floor((ST_Y(s.p)::numeric - ?::numeric) * ?::numeric + 0.5)::int " +
            "ORDER BY s.k";

    private final JdbcTemplate jdbc;

    public WalkSlopeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM walk_slope_cell", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * 격자 전체를 교체한다(한 트랜잭션). 도중에 실패하면 기존 격자가 그대로 남는다.
     * 테이블을 EXCLUSIVE 로 잠가 재적재가 겹치지 않게 하고, 읽기(경로 샘플링)는 막지 않는다.
     * 같은 입력으로 다시 실행해도 결과 행이 같다(멱등).
     *
     * @return 적재한 셀 수
     */
    @Transactional
    public int replaceAll(SlopeCells cells) {
        jdbc.execute("LOCK TABLE walk_slope_cell IN EXCLUSIVE MODE");
        jdbc.update("DELETE FROM walk_slope_cell");
        for (int from = 0; from < cells.size(); from += INSERT_CHUNK) {
            int to = Math.min(cells.size(), from + INSERT_CHUNK);
            insertChunk(cells, from, to);
        }
        return cells.size();
    }

    private void insertChunk(SlopeCells cells, int from, int to) {
        int n = to - from;
        Integer[] is = new Integer[n];
        Integer[] js = new Integer[n];
        Double[] slopes = new Double[n];
        Float[] elevations = new Float[n];
        for (int k = 0; k < n; k++) {
            is[k] = cells.cellI(from + k);
            js[k] = cells.cellJ(from + k);
            slopes[k] = cells.slopeDeg(from + k);
            elevations[k] = cells.elevationM(from + k);
        }
        jdbc.execute((ConnectionCallback<Void>) con -> {
            Array ai = con.createArrayOf("int4", is);
            Array aj = con.createArrayOf("int4", js);
            Array as = con.createArrayOf("float8", slopes);
            Array ae = con.createArrayOf("float4", elevations);
            try (PreparedStatement ps = con.prepareStatement(INSERT_SQL)) {
                ps.setArray(1, ai);
                ps.setArray(2, aj);
                ps.setArray(3, as);
                ps.setArray(4, ae);
                ps.executeUpdate();
            } finally {
                ai.free();
                aj.free();
                as.free();
                ae.free();
            }
            return null;
        });
    }

    /**
     * 경로 WKT(LINESTRING, 경도 위도 순)를 따라 경사를 읽는다 — SQL 문 1회.
     * 값이 없는 샘플은 NaN 이다.
     */
    public SlopeProfile sample(String lineWkt, WalkGrid grid, double intervalMeters, int maxSamples) {
        double[] length = {0};
        List<Double> slopes = new ArrayList<>();
        jdbc.query(SAMPLE_SQL, rs -> {
            length[0] = rs.getDouble(3);
            double s = rs.getDouble(2);
            slopes.add(rs.wasNull() ? Double.NaN : s);
        }, lineWkt, maxSamples, intervalMeters,
                grid.minLng(), grid.cellsPerDegree(), grid.minLat(), grid.cellsPerDegree());
        double[] out = slopes.stream().mapToDouble(Double::doubleValue).toArray();
        return new SlopeProfile(length[0], out);
    }
}
