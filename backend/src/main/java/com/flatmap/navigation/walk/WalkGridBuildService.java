package com.flatmap.navigation.walk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 경사 격자 적재 파이프라인: DEM 읽기 → Horn 경사 계산 → walk_slope_cell 교체.
 * 같은 DEM·같은 설정이면 몇 번을 돌려도 결과 행이 같다(전체 교체, 한 트랜잭션). 동시에 둘이 돌지 않는다.
 */
@Service
public class WalkGridBuildService {

    private static final Logger log = LoggerFactory.getLogger(WalkGridBuildService.class);

    /** @param gridCells 격자 전체 셀 수(cols × rows), @param loadedCells 실제 적재한 셀 수(DEM 가장자리·결측 제외) */
    public record BuildResult(int gridCells, int loadedCells, long elapsedMillis) {
    }

    /** DEM 원본이 없을 때. 컨트롤러가 사용자 안내(409)로 바꾼다. */
    public static class DemMissingException extends RuntimeException {
        public DemMissingException() {
            super("DEM 원본 없음");
        }
    }

    /** 다른 적재가 진행 중일 때. */
    public static class BuildInProgressException extends RuntimeException {
        public BuildInProgressException() {
            super("격자 적재 진행 중");
        }
    }

    private final DemSource demSource;
    private final WalkGrid grid;
    private final WalkSlopeRepository repository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WalkGridBuildService(DemSource demSource, WalkGrid grid, WalkSlopeRepository repository) {
        this.demSource = demSource;
        this.grid = grid;
        this.repository = repository;
    }

    public BuildResult rebuild() {
        if (!demSource.exists()) throw new DemMissingException();
        if (!running.compareAndSet(false, true)) throw new BuildInProgressException();
        try {
            long start = System.nanoTime();
            DemRaster dem = demSource.load();
            SlopeCells cells = SlopeGridBuilder.build(dem, grid);
            if (cells.size() == 0) {
                // 범위가 DEM 밖이거나 값이 모두 없다. 기존 격자를 비우지 않는다.
                throw new DemException("DEM 이 격자 범위를 덮지 않는다");
            }
            int loaded = repository.replaceAll(cells);
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("보행난이도 경사 격자 적재 완료: 격자 {}셀 중 {}셀, {}ms", grid.cols() * grid.rows(), loaded, ms);
            return new BuildResult(grid.cols() * grid.rows(), loaded, ms);
        } finally {
            running.set(false);
        }
    }
}
