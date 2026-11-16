package com.flatmap.navigation.walk;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 경사 격자 적재 파이프라인: 멱등, 전체 교체, 실패 시 기존 격자 보존. 합성 DEM 과 실제 PostGIS 를 쓴다. */
@IntegrationTest
class WalkGridBuildServiceTest {

    private static final double PX = 1.0 / 3600;
    private static final double MIN_LAT = 37.40, MIN_LNG = 127.10;
    private static final WalkGrid GRID = new WalkGrid(MIN_LAT, MIN_LAT + 6 * PX, MIN_LNG, MIN_LNG + 6 * PX, 3600);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    WalkSlopeRepository repository;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM walk_slope_cell");
    }

    /** SlopeGridBuilderTest 와 같은 배치의 11x11 합성 DEM. */
    private static DemRaster dem(BiFunction<Double, Double, Double> z) {
        int n = 11;
        float[] v = new float[n * n];
        double dx = PX * SlopeGridBuilder.metersPerDegreeLng(MIN_LAT);
        double dy = PX * SlopeGridBuilder.metersPerDegreeLat(MIN_LAT);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                v[row * n + col] = (float) (double) z.apply(col * dx, (n - 1 - row) * dy);
            }
        }
        return new DemRaster(n, n, MIN_LNG - 2 * PX - PX / 2, MIN_LAT + 8 * PX + PX / 2, PX, PX, v);
    }

    private static DemSource source(DemRaster raster) {
        return new DemSource() {
            public boolean exists() {
                return true;
            }

            public DemRaster load() {
                return raster;
            }
        };
    }

    private WalkGridBuildService service(DemSource s) {
        return new WalkGridBuildService(s, GRID, repository);
    }

    private long count() {
        return repository.count();
    }

    private double sumSlope() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(slope_deg), 0) FROM walk_slope_cell", Double.class);
    }

    @Test
    void runningThePipelineTwiceLeavesTheSameRows() {
        WalkGridBuildService s = service(source(dem((e, n) -> 100 + 0.1 * e)));

        WalkGridBuildService.BuildResult first = s.rebuild();
        long rows1 = count();
        double sum1 = sumSlope();
        WalkGridBuildService.BuildResult second = s.rebuild();

        assertThat(first.gridCells()).isEqualTo(49);
        assertThat(first.loadedCells()).isEqualTo(49);
        assertThat(second.loadedCells()).isEqualTo(49);
        assertThat(rows1).isEqualTo(49);
        assertThat(count()).as("두 번 실행해도 행 수 불변").isEqualTo(rows1);
        assertThat(sumSlope()).as("값도 같다").isEqualTo(sum1);
        // 경사 = atan(0.1) ≈ 5.71°
        assertThat(jdbc.queryForObject("SELECT slope_deg FROM walk_slope_cell WHERE cell_i = 3 AND cell_j = 3",
                Double.class)).isCloseTo(Math.toDegrees(Math.atan(0.1)), within(0.01));
    }

    @Test
    void aNewDemReplacesTheOldGridInsteadOfAddingToIt() {
        service(source(dem((e, n) -> 100 + 0.1 * e))).rebuild();
        double before = sumSlope();

        service(source(dem((e, n) -> 100 + 0.2 * e))).rebuild();

        assertThat(count()).isEqualTo(49);
        assertThat(sumSlope()).isGreaterThan(before * 1.5);
    }

    @Test
    void aFailedRebuildKeepsTheExistingGrid() {
        service(source(dem((e, n) -> 100 + 0.1 * e))).rebuild();
        long rows = count();
        double sum = sumSlope();

        // DEM 읽기 실패
        DemSource broken = new DemSource() {
            public boolean exists() {
                return true;
            }

            public DemRaster load() {
                throw new DemException("깨진 파일");
            }
        };
        assertThatThrownBy(() -> service(broken).rebuild()).isInstanceOf(DemException.class);
        assertThat(count()).isEqualTo(rows);
        assertThat(sumSlope()).isEqualTo(sum);

        // DEM 이 격자 범위를 덮지 않음(셀이 하나도 안 만들어짐) — 기존 격자를 비우지 않는다
        DemRaster nowhere = new DemRaster(11, 11, MIN_LNG + 1 - 2 * PX - PX / 2, MIN_LAT + 8 * PX + PX / 2,
                PX, PX, new float[121]);
        assertThatThrownBy(() -> service(source(nowhere)).rebuild()).isInstanceOf(DemException.class);
        assertThat(count()).isEqualTo(rows);

        // DEM 파일 없음
        DemSource missing = new DemSource() {
            public boolean exists() {
                return false;
            }

            public DemRaster load() {
                throw new AssertionError("없는 파일을 읽으면 안 된다");
            }
        };
        assertThatThrownBy(() -> service(missing).rebuild()).isInstanceOf(WalkGridBuildService.DemMissingException.class);
        assertThat(count()).isEqualTo(rows);
    }

    @Test
    void aFailureWhileInsertingRollsBackToTheOldGrid() {
        service(source(dem((e, n) -> 100 + 0.1 * e))).rebuild();
        long rows = count();
        double sum = sumSlope();

        SlopeCells duplicated = new SlopeCells(4);
        duplicated.add(1, 1, 2.0, 10f);
        duplicated.add(1, 1, 3.0, 10f);          // 기본키 중복 → INSERT 실패

        assertThatThrownBy(() -> repository.replaceAll(duplicated)).isInstanceOf(Exception.class);
        assertThat(count()).as("DELETE 까지 롤백").isEqualTo(rows);
        assertThat(sumSlope()).isEqualTo(sum);
    }

    @Test
    void onlyOneRebuildRunsAtATime() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DemSource slow = new DemSource() {
            public boolean exists() {
                return true;
            }

            public DemRaster load() {
                loading.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return dem((e, n) -> 100 + 0.1 * e);
            }
        };
        WalkGridBuildService s = service(slow);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<WalkGridBuildService.BuildResult> running = pool.submit(s::rebuild);
            assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(s::rebuild).isInstanceOf(WalkGridBuildService.BuildInProgressException.class);

            release.countDown();
            assertThat(running.get(30, TimeUnit.SECONDS).loadedCells()).isEqualTo(49);
            // 끝나면 다시 돌릴 수 있다
            assertThat(s.rebuild().loadedCells()).isEqualTo(49);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void theTableOnlyHoldsSlopeColumnsAndIsSeparateFromSteepSlopeAreas() {
        // 보행난이도 격자는 급경사지(붕괴위험) 테이블과 컬럼·참조를 섞지 않는다(DECISIONS §3, §4)
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'walk_slope_cell' ORDER BY ordinal_position", String.class))
                .containsExactly("cell_i", "cell_j", "slope_deg", "elevation_m");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'walk_slope_cell' AND constraint_type = 'FOREIGN KEY'", Integer.class)).isZero();
    }
}
