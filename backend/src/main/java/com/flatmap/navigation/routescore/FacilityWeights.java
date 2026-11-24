package com.flatmap.navigation.routescore;

/** 시설 종류별 가중치. 경로 A축과 시설 밀도 격자가 같은 값을 쓴다(설정: score.weight.*). */
public record FacilityWeights(double cctv, double securityLight, double police) {

    public FacilityWeights {
        if (cctv < 0 || securityLight < 0 || police < 0) {
            throw new IllegalArgumentException("가중치는 0 이상이어야 한다.");
        }
    }

    /** 가중 시설 수. */
    public double weighted(long cctvCount, long lightCount, long policeCount) {
        return cctvCount * cctv + lightCount * securityLight + policeCount * police;
    }
}
