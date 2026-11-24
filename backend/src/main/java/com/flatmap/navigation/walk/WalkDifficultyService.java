package com.flatmap.navigation.walk;

import com.flatmap.navigation.routescore.RouteGeometry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 경로의 보행난이도(B축) 평가: 경사 샘플링 쿼리 1회 + 순수 분류·점수.
 * 격자가 비어 있거나 경로가 격자 밖이면 빈 결과다(B축을 점수에 넣지 않는다).
 */
@Service
public class WalkDifficultyService {

    private final WalkSlopeRepository repository;
    private final WalkAssessor assessor;
    private final WalkGrid grid;
    private final WalkModel model;

    public WalkDifficultyService(WalkSlopeRepository repository, WalkAssessor assessor, WalkGrid grid,
                                 WalkModel model) {
        this.repository = repository;
        this.assessor = assessor;
        this.grid = grid;
        this.model = model;
    }

    public WalkMode defaultMode() {
        return model.defaultMode();
    }

    /** @param latLngPoints 경로 좌표({@code [위도, 경도]}) */
    public Optional<WalkAssessment> assess(List<double[]> latLngPoints, WalkMode mode) {
        if (latLngPoints == null || latLngPoints.size() < 2) return Optional.empty();
        SlopeProfile profile = repository.sample(RouteGeometry.toWkt(latLngPoints), grid,
                model.intervalMeters(), model.maxSamples());
        return assessor.assess(profile, mode);
    }
}
