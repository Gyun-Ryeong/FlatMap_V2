package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.walk.SlopeProfile;
import com.flatmap.navigation.walk.WalkAssessment;
import com.flatmap.navigation.walk.WalkAssessor;
import com.flatmap.navigation.walk.WalkMode;
import com.flatmap.navigation.walk.WalkModel;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** B축(보행난이도)이 점수에 합류하는 방식: breakdown 항목, 가중 평균, 데이터가 없을 때의 동작. */
class RouteScorerWalkAxisTest {

    private static final WeatherCorrection CORRECTION = new WeatherCorrection(1.8, 1.6, 1.4, 1.6, 1.2, 0, 10);

    private static ScoreModel scoreModel(double wA, double wB, double wC) {
        return new ScoreModel(7, new FacilityWeights(1, 1, 5), 50, 300, 0.1,
                PiecewiseScale.parse("0:0,2:20,5:40,10:60,20:80,40:100"), wA, wB, wC);
    }

    private static WalkAssessor assessor() {
        Map<WalkMode, WalkModel.Thresholds> t = new EnumMap<>(WalkMode.class);
        t.put(WalkMode.GENERAL, new WalkModel.Thresholds(4.76, 7.13));
        t.put(WalkMode.WEAK, new WalkModel.Thresholds(3.18, 4.76));
        return new WalkAssessor(new WalkModel(30, 2000, 0.5, WalkMode.GENERAL, t, 1.0, 0.5, 0.0));
    }

    private static WalkAssessment walk(WalkMode mode, double... slopes) {
        return assessor().assess(new SlopeProfile(30.0 * (slopes.length - 1), slopes), mode).orElseThrow();
    }

    private static WeatherInfo clear() {
        WeatherInfo w = new WeatherInfo();
        w.setPrecipitationType(0);
        w.setTemperature(20);
        w.setWindSpeed(1);
        return w;
    }

    private static final RouteExposure EXPOSURE = new RouteExposure(3, 4, 1, 1000);   // A = 64.0

    @Test
    void bAxisIsAddedToTheBreakdownBetweenAAndC() {
        RouteScorer scorer = new RouteScorer(scoreModel(1.0, 1.0, 0.25), CORRECTION);
        WalkAssessment w = walk(WalkMode.GENERAL, 0, 0, 0, 0, 6, 6, 6, 9);   // 양호 4 · 주의 3 · 위험 1 → 4/8 + 3/16 = 68.75 → 68.8

        RouteScore s = scorer.score(EXPOSURE, clear(), w);

        assertThat(s.breakdown()).extracting(AxisScore::axis).containsExactly("A", "B", "C");
        AxisScore b = s.breakdown().get(1);
        assertThat(b.name()).isEqualTo("보행난이도");
        assertThat(b.score()).isEqualTo(w.score()).isEqualTo(68.8);
        assertThat(b.weight()).isEqualTo(1.0);
        assertThat(b.detail()).containsEntry("mode", "GENERAL").containsEntry("goodPercent", 50.0)
                .containsEntry("cautionPercent", 37.5).containsEntry("dangerPercent", 12.5)
                .containsEntry("maxSlopeDeg", 9.0).containsKey("limitation");
        // 총점 = 가중 평균: (A 64 × 1 + B 68.8 × 1 + C 100 × 0.25) / 2.25
        assertThat(s.totalScore()).isCloseTo((64.0 + 68.8 + 25.0) / 2.25, within(0.06));
    }

    @Test
    void withoutAnAssessmentTheScoreIsExactlyAsBefore() {
        RouteScorer scorer = new RouteScorer(scoreModel(1.0, 1.0, 0.25), CORRECTION);
        RouteScore before = scorer.score(EXPOSURE, clear());
        RouteScore noWalk = scorer.score(EXPOSURE, clear(), null);
        assertThat(noWalk.breakdown()).extracting(AxisScore::axis).containsExactly("A", "C");
        assertThat(noWalk.totalScore()).isEqualTo(before.totalScore());
        assertThat(scorer.score(EXPOSURE, null, null).breakdown()).extracting(AxisScore::axis).containsExactly("A");
    }

    @Test
    void aSteeperRouteGetsALowerTotalAndTheWeakModeLowerStill() {
        RouteScorer scorer = new RouteScorer(scoreModel(1.0, 1.0, 0.25), CORRECTION);
        double[] gentle = {1, 1, 1, 1, 1, 1, 1, 1};
        double[] steep = {1, 1, 1, 6, 6, 9, 9, 9};
        double gentleTotal = scorer.score(EXPOSURE, null, walk(WalkMode.GENERAL, gentle)).totalScore();
        double steepTotal = scorer.score(EXPOSURE, null, walk(WalkMode.GENERAL, steep)).totalScore();
        assertThat(steepTotal).isLessThan(gentleTotal);

        double[] mid = {1, 1, 4, 4, 4, 5, 6, 6};
        double general = scorer.score(EXPOSURE, null, walk(WalkMode.GENERAL, mid)).totalScore();
        double weak = scorer.score(EXPOSURE, null, walk(WalkMode.WEAK, mid)).totalScore();
        assertThat(weak).isLessThan(general);
    }

    @Test
    void bWeightComesFromTheModelAndMustBePositive() {
        WalkAssessment w = walk(WalkMode.GENERAL, 9, 9, 9, 9);                       // B = 0
        double heavy = new RouteScorer(scoreModel(1.0, 3.0, 0.25), CORRECTION)
                .score(EXPOSURE, null, w).totalScore();
        double light = new RouteScorer(scoreModel(1.0, 0.5, 0.25), CORRECTION)
                .score(EXPOSURE, null, w).totalScore();
        assertThat(heavy).isLessThan(light);          // B 가 0점이면 B 가중치가 클수록 총점이 낮다
        assertThatThrownBy(() -> scoreModel(1.0, 0, 0.25)).isInstanceOf(IllegalArgumentException.class);
    }
}
