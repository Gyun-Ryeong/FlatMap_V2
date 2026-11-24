package com.flatmap.navigation.walk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 경로 하나의 보행난이도 평가(B축 입력). 비율은 값이 있는 샘플 기준 백분율이며 세 값의 합은 정확히 100.0 이다.
 *
 * @param score      0~100, 높을수록 오르막 부담이 작다(양호 구간이 많다). 설계 파라미터 기반 상대 값.
 * @param coverage   샘플 중 격자에 값이 있는 비율(0~1)
 */
public record WalkAssessment(
        WalkMode mode,
        double score,
        double goodPercent,
        double cautionPercent,
        double dangerPercent,
        int sampleCount,
        int dataCount,
        double coverage,
        double lengthMeters,
        double maxSlopeDeg,
        double meanSlopeDeg,
        WalkModel.Thresholds thresholds,
        double sampleIntervalMeters) {

    /** 응답 breakdown.detail 에 들어가는 입력값. 단위는 경사 각도(°), 길이 미터, 비율 %. */
    public Map<String, Object> detail() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("mode", mode.name());
        d.put("goodMaxDeg", thresholds.goodMaxDeg());
        d.put("cautionMaxDeg", thresholds.cautionMaxDeg());
        d.put("goodPercent", goodPercent);
        d.put("cautionPercent", cautionPercent);
        d.put("dangerPercent", dangerPercent);
        d.put("maxSlopeDeg", round1(maxSlopeDeg));
        d.put("meanSlopeDeg", round1(meanSlopeDeg));
        d.put("routeLengthMeters", round1(lengthMeters));
        d.put("sampleCount", sampleCount);
        d.put("sampleWithDataCount", dataCount);
        d.put("coverage", Math.round(coverage * 1000.0) / 1000.0);
        d.put("sampleIntervalMeters", round1(sampleIntervalMeters));
        d.put("limitation", WalkModel.LIMITATION);
        return d;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
