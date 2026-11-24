package com.flatmap.navigation.density;

import com.flatmap.navigation.routescore.ScoreModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 시설 밀도 격자 재계산·조회.
 *
 * <p>재계산은 <b>단일 트랜잭션</b>이다: 중간에 실패하면 기존 격자가 그대로 남는다.
 * 시설 0개이면서 인접 8칸에도 시설이 없는 셀은 저장하지 않는다.
 */
@Service
public class FacilityDensityGridService {

    private static final Logger log = LoggerFactory.getLogger(FacilityDensityGridService.class);

    /** 재계산 결과 요약. */
    public record RecalcResult(int totalCells, Map<DensityLevel, Integer> byLevel, int scoreVersion) {
    }

    private final FacilityDensityGridRepository repository;
    private final GridSpec spec;
    private final DensityScorer scorer;
    private final ScoreModel scoreModel;

    public FacilityDensityGridService(FacilityDensityGridRepository repository, GridSpec spec,
                                      DensityScorer scorer, ScoreModel scoreModel) {
        this.repository = repository;
        this.spec = spec;
        this.scorer = scorer;
        this.scoreModel = scoreModel;
    }

    @Transactional
    public RecalcResult recalculate() {
        log.info("시설 밀도 격자 재계산 시작 ({}x{} 셀)", spec.cols(), spec.rows());
        repository.lockForRewrite();

        List<GridCellCounts> counted = repository.aggregateCells(spec);
        List<FacilityDensityGridRepository.ScoredCell> scored = new ArrayList<>(counted.size());
        Map<DensityLevel, Integer> byLevel = new EnumMap<>(DensityLevel.class);
        for (DensityLevel level : DensityLevel.values()) byLevel.put(level, 0);

        for (GridCellCounts c : counted) {
            BigDecimal score = scorer.score(c.cctv(), c.light(), c.police());
            DensityLevel level = scorer.level(score);
            scored.add(new FacilityDensityGridRepository.ScoredCell(c, score, level));
            byLevel.merge(level, 1, Integer::sum);
        }

        repository.deleteAll();
        repository.insertAll(scored, spec, scoreModel.version());

        log.info("시설 밀도 격자 재계산 완료: {}개 셀 {}", scored.size(), byLevel);
        return new RecalcResult(scored.size(), byLevel, scoreModel.version());
    }

    public List<DensityCell> findCells() {
        return repository.findAll();
    }

    public List<DensityCell> findCellsIntersecting(double minLat, double minLng, double maxLat, double maxLng) {
        return repository.findIntersecting(minLat, minLng, maxLat, maxLng);
    }
}
