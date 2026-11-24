package com.flatmap.navigation.density;

import com.flatmap.navigation.routescore.FacilityWeights;
import com.flatmap.navigation.routescore.PiecewiseScale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DensityScorerTest {

    // SafetyRoad 의 시설 수→점수 공식과 같은 꺾은점, 경찰 가중 5, 경계 30/60
    private final DensityScorer scorer = new DensityScorer(new FacilityWeights(1, 1, 5),
            PiecewiseScale.parse("0:10,1:30,2:40,5:61,10:81,22.67:100"), 30, 60);

    private BigDecimal scoreOfWeighted(int cctvOnly) {
        return scorer.score(cctvOnly, 0, 0);
    }

    @Test
    void matchesTheSafetyRoadFormulaForIntegerCounts() {
        // 옛 공식: 0→10, 1~2→20+10n, 3~5→40+(n-2)*7, 6~10→61+(n-5)*4, 11↑→min(100, 81+(n-10)*1.5)
        assertThat(scoreOfWeighted(0)).isEqualByComparingTo("10.00");
        assertThat(scoreOfWeighted(1)).isEqualByComparingTo("30.00");
        assertThat(scoreOfWeighted(2)).isEqualByComparingTo("40.00");
        assertThat(scoreOfWeighted(3)).isEqualByComparingTo("47.00");
        assertThat(scoreOfWeighted(5)).isEqualByComparingTo("61.00");
        assertThat(scoreOfWeighted(6)).isEqualByComparingTo("65.00");
        assertThat(scoreOfWeighted(10)).isEqualByComparingTo("81.00");
        assertThat(scoreOfWeighted(11)).isEqualByComparingTo("82.50");
        assertThat(scoreOfWeighted(22)).isEqualByComparingTo("99.00");
        assertThat(scoreOfWeighted(23)).isEqualByComparingTo("100.00");
        assertThat(scoreOfWeighted(500)).isEqualByComparingTo("100.00");
    }

    @Test
    void policeIsWeightedOnceByTheConfiguredWeight() {
        // 경찰 1개 = 가중 5 → 61점. (SafetyRoad 의 실질 6배가 아니다)
        assertThat(scorer.score(0, 0, 1)).isEqualByComparingTo("61.00");
        assertThat(scorer.score(2, 1, 1)).isEqualByComparingTo(scorer.score(8, 0, 0));
    }

    @Test
    void levelBoundariesAreInclusiveOnTheUpperSide() {
        assertThat(scorer.level(new BigDecimal("10.00"))).isEqualTo(DensityLevel.LOW_DENSITY);
        assertThat(scorer.level(new BigDecimal("30.00"))).isEqualTo(DensityLevel.LOW_DENSITY);
        assertThat(scorer.level(new BigDecimal("30.01"))).isEqualTo(DensityLevel.MEDIUM);
        assertThat(scorer.level(new BigDecimal("60.00"))).isEqualTo(DensityLevel.MEDIUM);
        assertThat(scorer.level(new BigDecimal("60.01"))).isEqualTo(DensityLevel.HIGH_DENSITY);
        assertThat(scorer.level(new BigDecimal("100.00"))).isEqualTo(DensityLevel.HIGH_DENSITY);
    }

    @Test
    void levelsForTypicalCounts() {
        assertThat(scorer.level(scoreOfWeighted(0))).isEqualTo(DensityLevel.LOW_DENSITY);   // 10점
        assertThat(scorer.level(scoreOfWeighted(1))).isEqualTo(DensityLevel.LOW_DENSITY);   // 30점 (경계 포함)
        assertThat(scorer.level(scoreOfWeighted(2))).isEqualTo(DensityLevel.MEDIUM);        // 40점
        assertThat(scorer.level(scoreOfWeighted(5))).isEqualTo(DensityLevel.HIGH_DENSITY);  // 61점
    }

    @Test
    void levelNamesDoNotUseDangerWording() {
        for (DensityLevel level : DensityLevel.values()) {
            assertThat(level.name()).doesNotContain("RISK", "DANGER", "HIGH_RISK");
        }
        assertThat(DensityLevel.values()).extracting(Enum::name)
                .containsExactly("LOW_DENSITY", "MEDIUM", "HIGH_DENSITY");
    }

    @Test
    void rejectsInvertedBoundaries() {
        assertThatThrownBy(() -> new DensityScorer(new FacilityWeights(1, 1, 5),
                PiecewiseScale.parse("0:10,10:90"), 60, 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
