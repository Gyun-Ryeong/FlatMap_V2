package com.flatmap.navigation.density;

import com.flatmap.navigation.routescore.FacilityWeights;
import com.flatmap.navigation.routescore.PiecewiseScale;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 격자 셀의 밀도 점수와 등급. 점수 변환은 {@link PiecewiseScale} 한 곳을 쓰고 경계값은 모두 설정에서 온다.
 * 점수가 낮다는 것은 시설 데이터가 적다는 뜻이지 위험하다는 뜻이 아니다.
 */
public class DensityScorer {

    private final FacilityWeights weights;
    private final PiecewiseScale scale;
    private final double lowDensityMaxScore;
    private final double mediumMaxScore;

    public DensityScorer(FacilityWeights weights, PiecewiseScale scale,
                         double lowDensityMaxScore, double mediumMaxScore) {
        if (lowDensityMaxScore >= mediumMaxScore) {
            throw new IllegalArgumentException("등급 경계는 low-density-max-score < medium-max-score 여야 한다.");
        }
        this.weights = weights;
        this.scale = scale;
        this.lowDensityMaxScore = lowDensityMaxScore;
        this.mediumMaxScore = mediumMaxScore;
    }

    /** 0~100, 소수 둘째 자리(DB numeric(5,2))까지. */
    public BigDecimal score(long cctv, long light, long police) {
        double weighted = weights.weighted(cctv, light, police);
        return BigDecimal.valueOf(scale.apply(weighted)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 저장되는(반올림된) 점수로 등급을 가른다: <= 하한 LOW_DENSITY, <= 중간 MEDIUM, 그 위 HIGH_DENSITY. */
    public DensityLevel level(BigDecimal score) {
        double s = score.doubleValue();
        if (s <= lowDensityMaxScore) return DensityLevel.LOW_DENSITY;
        if (s <= mediumMaxScore) return DensityLevel.MEDIUM;
        return DensityLevel.HIGH_DENSITY;
    }
}
