package com.flatmap.navigation.walk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 보행난이도 분류·점수(순수 계산). 임계값은 DECISIONS §4 의 값이다(경사 각도 °). */
class WalkAssessorTest {

    static WalkModel model(double minCoverage, double wGood, double wCaution, double wDanger) {
        Map<WalkMode, WalkModel.Thresholds> t = new EnumMap<>(WalkMode.class);
        t.put(WalkMode.GENERAL, new WalkModel.Thresholds(4.76, 7.13));
        t.put(WalkMode.WEAK, new WalkModel.Thresholds(3.18, 4.76));
        return new WalkModel(30, 2000, minCoverage, WalkMode.GENERAL, t, wGood, wCaution, wDanger);
    }

    private final WalkModel model = model(0.5, 1.0, 0.5, 0.0);
    private final WalkAssessor assessor = new WalkAssessor(model);

    private static SlopeProfile profile(double... slopes) {
        return new SlopeProfile(30.0 * Math.max(1, slopes.length - 1), slopes);
    }

    // ---- 임계값 경계: 바로 아래 / 정확히 / 바로 위 ----

    @ParameterizedTest(name = "{0} {1}° → {2}")
    @CsvSource({
            // WEAK: ≤ 3.18 양호, ≤ 4.76 주의, 그 위 위험
            "WEAK, 3.18, GOOD", "WEAK, 4.76, CAUTION",
            // GENERAL: ≤ 4.76 양호, ≤ 7.13 주의, 그 위 위험
            "GENERAL, 4.76, GOOD", "GENERAL, 7.13, CAUTION"})
    void boundaryValueItselfBelongsToTheLowerClass(WalkMode mode, double slope, SlopeClass expected) {
        assertThat(model.classify(mode, slope)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1}°: 바로 아래 {2}, 바로 위 {3}")
    @CsvSource({
            "WEAK, 3.18, GOOD, CAUTION",
            "WEAK, 4.76, CAUTION, DANGER",
            "GENERAL, 4.76, GOOD, CAUTION",
            "GENERAL, 7.13, CAUTION, DANGER"})
    void justBelowAndJustAboveTheBoundary(WalkMode mode, double boundary, SlopeClass below, SlopeClass above) {
        assertThat(model.classify(mode, Math.nextDown(boundary))).isEqualTo(below);
        assertThat(model.classify(mode, Math.nextUp(boundary))).isEqualTo(above);
    }

    @Test
    void sameSlopeIsClassifiedStricterForWeakWalkers() {
        // 4.0° 는 일반에서는 양호, 보행약자에서는 주의. 6.0° 는 일반 주의, 보행약자 위험.
        assertThat(model.classify(WalkMode.GENERAL, 4.0)).isEqualTo(SlopeClass.GOOD);
        assertThat(model.classify(WalkMode.WEAK, 4.0)).isEqualTo(SlopeClass.CAUTION);
        assertThat(model.classify(WalkMode.GENERAL, 6.0)).isEqualTo(SlopeClass.CAUTION);
        assertThat(model.classify(WalkMode.WEAK, 6.0)).isEqualTo(SlopeClass.DANGER);
    }

    // ---- 구간 비율과 점수 ----

    @Test
    void ratiosAndScoreFromAKnownProfile() {
        // 10개 샘플: 평지 4, 4.0° 3, 6.0° 2, 9.0° 1  (GENERAL: 양호 7 / 주의 2 / 위험 1)
        SlopeProfile p = profile(0, 0, 0, 0, 4.0, 4.0, 4.0, 6.0, 6.0, 9.0);
        WalkAssessment g = assessor.assess(p, WalkMode.GENERAL).orElseThrow();
        assertThat(g.goodPercent()).isEqualTo(70.0);
        assertThat(g.cautionPercent()).isEqualTo(20.0);
        assertThat(g.dangerPercent()).isEqualTo(10.0);
        assertThat(g.score()).isEqualTo(80.0);          // 100 × (0.7×1 + 0.2×0.5 + 0.1×0)
        assertThat(g.maxSlopeDeg()).isEqualTo(9.0);
        assertThat(g.meanSlopeDeg()).isCloseTo(3.3, within(1e-9));
        assertThat(g.coverage()).isEqualTo(1.0);
        // WEAK: 양호 4 / 주의 3 / 위험 3
        WalkAssessment w = assessor.assess(p, WalkMode.WEAK).orElseThrow();
        assertThat(w.goodPercent()).isEqualTo(40.0);
        assertThat(w.cautionPercent()).isEqualTo(30.0);
        assertThat(w.dangerPercent()).isEqualTo(30.0);
        assertThat(w.score()).isEqualTo(55.0);          // 100 × (0.4 + 0.15)
    }

    @Test
    void theSameRouteIsWorseForWeakWalkersAndNeverBetter() {
        Random rnd = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            double[] slopes = rnd.doubles(40, 0, 12).toArray();
            double general = assessor.assess(profile(slopes), WalkMode.GENERAL).orElseThrow().score();
            double weak = assessor.assess(profile(slopes), WalkMode.WEAK).orElseThrow().score();
            assertThat(weak).isLessThanOrEqualTo(general);
        }
        // 경계 사이(3.18~7.13°)에 걸친 경로는 확실히 더 나쁘다
        SlopeProfile mid = profile(4, 4, 4, 5, 5, 6, 6, 6);
        assertThat(assessor.assess(mid, WalkMode.WEAK).orElseThrow().score())
                .isLessThan(assessor.assess(mid, WalkMode.GENERAL).orElseThrow().score());
    }

    @Test
    void percentagesAlwaysSumToExactlyOneHundred() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 500; trial++) {
            int n = 1 + rnd.nextInt(97);
            double[] slopes = rnd.doubles(n, 0, 12).toArray();
            for (WalkMode mode : WalkMode.values()) {
                WalkAssessment a = assessor.assess(profile(slopes), mode).orElseThrow();
                // 소수 첫째 자리 값을 정수(0.1 단위)로 바꿔 더한다 — 부동소수점 오차 없이 정확히 1000
                long tenths = Math.round(a.goodPercent() * 10) + Math.round(a.cautionPercent() * 10)
                        + Math.round(a.dangerPercent() * 10);
                assertThat(tenths).as("n=%d mode=%s", n, mode).isEqualTo(1000);
                assertThat(a.goodPercent()).isBetween(0.0, 100.0);
            }
        }
        // 1/3 씩 나뉘는 경우도 합이 100.0
        WalkAssessment third = assessor.assess(profile(0, 4, 9), WalkMode.GENERAL).orElseThrow();
        assertThat(Math.round(third.goodPercent() * 10) + Math.round(third.cautionPercent() * 10)
                + Math.round(third.dangerPercent() * 10)).isEqualTo(1000);
    }

    @Test
    void scoreNeverImprovesAsTheTerrainGetsSteeper() {
        for (WalkMode mode : WalkMode.values()) {
            double previous = Double.MAX_VALUE;
            for (double slope = 0; slope <= 20; slope += 0.25) {
                double[] flat = new double[20];
                Arrays.fill(flat, slope);
                double score = assessor.assess(profile(flat), mode).orElseThrow().score();
                assertThat(score).as("%s %.2f°", mode, slope).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }
        // 한 구간이 더 가팔라지면 점수는 같거나 내려간다
        double base = assessor.assess(profile(1, 1, 1, 1), WalkMode.GENERAL).orElseThrow().score();
        double oneCaution = assessor.assess(profile(1, 1, 1, 6), WalkMode.GENERAL).orElseThrow().score();
        double oneDanger = assessor.assess(profile(1, 1, 1, 9), WalkMode.GENERAL).orElseThrow().score();
        assertThat(base).isGreaterThan(oneCaution);
        assertThat(oneCaution).isGreaterThan(oneDanger);
    }

    @Test
    void scoreStaysInZeroToHundred() {
        double[] flat = new double[10];
        double[] steep = new double[10];
        Arrays.fill(steep, 45);
        assertThat(assessor.assess(profile(flat), WalkMode.WEAK).orElseThrow().score()).isEqualTo(100.0);
        assertThat(assessor.assess(profile(steep), WalkMode.GENERAL).orElseThrow().score()).isEqualTo(0.0);
    }

    // ---- 데이터가 없는 구간 ----

    @Test
    void missingSamplesAreNotCountedAsGoodOrDangerous() {
        double nan = Double.NaN;
        // 8개 중 6개만 값이 있음(75%) — 비율은 값이 있는 6개 기준
        WalkAssessment a = assessor.assess(profile(0, 0, 0, 9, nan, nan, 0, 0), WalkMode.GENERAL).orElseThrow();
        assertThat(a.sampleCount()).isEqualTo(8);
        assertThat(a.dataCount()).isEqualTo(6);
        assertThat(a.coverage()).isEqualTo(0.75);
        assertThat(a.goodPercent()).isCloseTo(83.3, within(0.05));
        assertThat(a.dangerPercent()).isCloseTo(16.7, within(0.05));
    }

    @Test
    void tooLittleDataMeansNoAssessmentRatherThanAGuess() {
        double nan = Double.NaN;
        assertThat(assessor.assess(profile(nan, nan, nan, nan), WalkMode.GENERAL)).isEmpty();          // 0%
        assertThat(assessor.assess(profile(0, nan, nan, nan), WalkMode.GENERAL)).isEmpty();            // 25% < 50%
        assertThat(assessor.assess(profile(0, 0, nan, nan), WalkMode.GENERAL)).isPresent();            // 50% (경계 포함)
        assertThat(assessor.assess(profile(), WalkMode.GENERAL)).isEmpty();
    }

    // ---- 설정 검증 ----

    @Test
    void detailCarriesTheInputsAndTheLimitation() {
        WalkAssessment a = assessor.assess(profile(0, 4.0, 6.0, 9.0), WalkMode.WEAK).orElseThrow();
        Map<String, Object> d = a.detail();
        assertThat(d).containsEntry("mode", "WEAK").containsEntry("goodMaxDeg", 3.18)
                .containsEntry("cautionMaxDeg", 4.76).containsEntry("sampleCount", 4)
                .containsEntry("coverage", 1.0).containsKey("limitation");
        assertThat((String) d.get("limitation")).contains("실제 경사보다 낮게");
    }

    @Test
    void modelRejectsInconsistentSettings() {
        Map<WalkMode, WalkModel.Thresholds> ok = new EnumMap<>(WalkMode.class);
        ok.put(WalkMode.GENERAL, new WalkModel.Thresholds(4.76, 7.13));
        ok.put(WalkMode.WEAK, new WalkModel.Thresholds(3.18, 4.76));
        // 경사가 클수록 점수가 좋아지는 가중
        assertThatThrownBy(() -> new WalkModel(30, 2000, 0.5, WalkMode.GENERAL, ok, 0.5, 1.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkModel(30, 2000, 0.5, WalkMode.GENERAL, ok, 1.0, 0.5, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        // 보행약자 기준이 일반보다 느슨
        Map<WalkMode, WalkModel.Thresholds> inverted = new EnumMap<>(WalkMode.class);
        inverted.put(WalkMode.GENERAL, new WalkModel.Thresholds(3.18, 4.76));
        inverted.put(WalkMode.WEAK, new WalkModel.Thresholds(4.76, 7.13));
        assertThatThrownBy(() -> new WalkModel(30, 2000, 0.5, WalkMode.GENERAL, inverted, 1.0, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkModel.Thresholds(4.76, 4.76)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkModel(0, 2000, 0.5, WalkMode.GENERAL, ok, 1.0, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalkModel(30, 2000, 0, WalkMode.GENERAL, ok, 1.0, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void walkModeParsing() {
        assertThat(WalkMode.parse("weak", WalkMode.GENERAL)).isEqualTo(WalkMode.WEAK);
        assertThat(WalkMode.parse(" GENERAL ", WalkMode.WEAK)).isEqualTo(WalkMode.GENERAL);
        assertThat(WalkMode.parse(null, WalkMode.GENERAL)).isEqualTo(WalkMode.GENERAL);
        assertThat(WalkMode.parse("", WalkMode.WEAK)).isEqualTo(WalkMode.WEAK);
        assertThatThrownBy(() -> WalkMode.parse("fast", WalkMode.GENERAL)).isInstanceOf(InvalidWalkModeException.class);
    }
}
