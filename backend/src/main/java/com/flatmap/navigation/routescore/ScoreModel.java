package com.flatmap.navigation.routescore;

/**
 * 경로 점수 모델의 설정값 묶음. 모든 값은 application.properties(score.*)에서 오며 코드에 하드코딩하지 않는다.
 * 이 값들은 검증되지 않은 설계 파라미터다.
 */
public record ScoreModel(
        int version,
        FacilityWeights weights,
        double radiusMeters,
        double policeRadiusMeters,
        double minRouteKm,
        PiecewiseScale exposureScale,
        double axisWeightA) {

    public ScoreModel {
        if (radiusMeters <= 0 || policeRadiusMeters <= 0) {
            throw new IllegalArgumentException("반경은 0 보다 커야 한다.");
        }
        if (minRouteKm <= 0) {
            throw new IllegalArgumentException("최소 경로 길이는 0 보다 커야 한다.");
        }
        if (axisWeightA <= 0) {
            throw new IllegalArgumentException("축 가중치는 0 보다 커야 한다.");
        }
    }
}
