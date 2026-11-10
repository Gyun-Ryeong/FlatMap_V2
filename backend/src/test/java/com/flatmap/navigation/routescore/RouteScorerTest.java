package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.WeatherInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 설정값은 테스트용 모델을 직접 만들어 쓴다 — 점수 함수가 설정에서만 값을 받는지 함께 확인한다. */
class RouteScorerTest {

    private static ScoreModel model(double policeWeight, double axisWeightA) {
        return new ScoreModel(7, new FacilityWeights(1, 1, policeWeight), 50, 300, 0.1,
                PiecewiseScale.parse("0:0,2:20,5:40,10:60,20:80,40:100"), axisWeightA, 0.25);
    }

    private static final WeatherCorrection CORRECTION = new WeatherCorrection(1.8, 1.6, 1.4, 1.6, 1.2, 0, 10);

    private static RouteScorer scorer(ScoreModel m) {
        return new RouteScorer(m, CORRECTION);
    }

    private static WeatherInfo weather(int pty, double temp, double wind) {
        WeatherInfo w = new WeatherInfo();
        w.setPrecipitationType(pty);
        w.setTemperature(temp);
        w.setWindSpeed(wind);
        return w;
    }

    private final RouteScorer scorer = scorer(model(5, 1.0));

    @Test
    void noFacilitiesScoresZero() {
        RouteScore s = scorer.score(new RouteExposure(0, 0, 0, 1000));
        assertThat(s.totalScore()).isEqualTo(0.0);
    }

    @Test
    void weightedCountPerKmGoesThroughTheSegmentFunction() {
        // 1km 경로, CCTV 3 + 보안등 4 + 경찰 1(가중 5) = 가중 12 → 12/km
        // 구간 10~20: 60 + (12-10) * (20/10) = 64
        RouteScore s = scorer.score(new RouteExposure(3, 4, 1, 1000));
        assertThat(s.totalScore()).isCloseTo(64.0, within(1e-9));
    }

    @Test
    void segmentBoundaryValuesAreExact() {
        assertThat(scorer.score(new RouteExposure(2, 0, 0, 1000)).totalScore()).isEqualTo(20.0);  // 2/km
        assertThat(scorer.score(new RouteExposure(5, 0, 0, 1000)).totalScore()).isEqualTo(40.0);  // 5/km
        assertThat(scorer.score(new RouteExposure(10, 0, 0, 1000)).totalScore()).isEqualTo(60.0); // 10/km
        assertThat(scorer.score(new RouteExposure(40, 0, 0, 1000)).totalScore()).isEqualTo(100.0);
        assertThat(scorer.score(new RouteExposure(400, 0, 0, 1000)).totalScore()).isEqualTo(100.0);
    }

    @Test
    void lengthIsFlooredSoTinyRoutesDoNotExplode() {
        // 10m 경로에 CCTV 1개: 길이를 0.1km 로 올려 1/0.1 = 10/km (0.01km 로 나누면 100/km)
        RouteScore s = scorer.score(new RouteExposure(1, 0, 0, 10));
        assertThat(s.totalScore()).isEqualTo(60.0);
    }

    @Test
    void policeWeightComesFromTheModel() {
        RouteExposure e = new RouteExposure(0, 0, 1, 1000);
        assertThat(scorer(model(5, 1.0)).score(e).totalScore()).isEqualTo(40.0);  // 가중 5/km
        assertThat(scorer(model(1, 1.0)).score(e).totalScore()).isEqualTo(10.0);  // 가중 1/km
    }

    @Test
    void breakdownCarriesAxisInputsAndTotalEqualsTheOnlyAxis() {
        RouteScore s = scorer.score(new RouteExposure(3, 4, 1, 1000));
        assertThat(s.breakdown()).hasSize(1);
        AxisScore a = s.breakdown().get(0);
        assertThat(a.axis()).isEqualTo("A");
        assertThat(a.name()).isEqualTo("야간 시설 노출");
        assertThat(a.score()).isEqualTo(s.totalScore());
        assertThat(a.weight()).isEqualTo(1.0);
        assertThat(a.detail()).containsEntry("cctvCount", 3L).containsEntry("lightCount", 4L)
                .containsEntry("policeCount", 1L).containsEntry("weightedCount", 12.0)
                .containsEntry("weightedPerKm", 12.0).containsEntry("radiusMeters", 50.0)
                .containsEntry("policeRadiusMeters", 300.0);
    }

    @Test
    void totalIsAWeightedAverageSoAxisWeightDoesNotChangeASingleAxisTotal() {
        RouteExposure e = new RouteExposure(3, 4, 1, 1000);
        assertThat(scorer(model(5, 2.5)).score(e).totalScore())
                .isEqualTo(scorer(model(5, 1.0)).score(e).totalScore());
    }

    // ---- C축(날씨 보정) ----

    @Test
    void clearWeatherAddsACAxisOf100AndTotalIsTheWeightedAverage() {
        // A = 64 (가중 12/km), C = 100 / 1.0 = 100, 가중치 A 1.0 · C 0.25 → (64 + 25) / 1.25 = 71.2
        RouteScore s = scorer.score(new RouteExposure(3, 4, 1, 1000), weather(0, 20, 1));
        assertThat(s.breakdown()).extracting(AxisScore::axis).containsExactly("A", "C");
        AxisScore c = s.breakdown().get(1);
        assertThat(c.name()).isEqualTo("날씨 보정");
        assertThat(c.score()).isEqualTo(100.0);
        assertThat(c.weight()).isEqualTo(0.25);
        assertThat(c.detail()).containsEntry("condition", "NONE").containsEntry("multiplier", 1.0);
        assertThat(s.totalScore()).isCloseTo(71.2, within(1e-9));
    }

    @Test
    void rainLowersTheTotalByTheConfiguredMultiplier() {
        // 비 → 계수 1.4 → C = 100/1.4 = 71.4. 총점 = (64 + 71.4 * 0.25) / 1.25 = 65.48 → 65.5
        RouteScore s = scorer.score(new RouteExposure(3, 4, 1, 1000), weather(1, 15, 1));
        AxisScore c = s.breakdown().get(1);
        assertThat(c.score()).isEqualTo(71.4);
        assertThat(c.detail()).containsEntry("condition", "RAIN").containsEntry("multiplier", 1.4);
        assertThat(s.totalScore()).isEqualTo(65.5);
        // 맑을 때(71.2)보다 낮다
        assertThat(s.totalScore()).isLessThan(
                scorer.score(new RouteExposure(3, 4, 1, 1000), weather(0, 20, 1)).totalScore());
    }

    @Test
    void multiplierComesFromConfigurationNotFromCode() {
        RouteScorer stronger = new RouteScorer(model(5, 1.0), new WeatherCorrection(2.5, 1.6, 2.0, 1.6, 1.2, 0, 10));
        AxisScore c = stronger.score(new RouteExposure(0, 0, 0, 1000), weather(1, 15, 1)).breakdown().get(1);
        assertThat(c.score()).isEqualTo(50.0);   // 100 / 2.0
    }

    @Test
    void fallbackOrMissingWeatherLeavesCAxisOutAndKeepsTheATotal() {
        RouteExposure e = new RouteExposure(3, 4, 1, 1000);
        RouteScore noWeather = scorer.score(e, null);
        RouteScore fallback = scorer.score(e, WeatherInfo.fallback());
        assertThat(noWeather.breakdown()).extracting(AxisScore::axis).containsExactly("A");
        assertThat(fallback.breakdown()).extracting(AxisScore::axis).containsExactly("A");
        assertThat(fallback.totalScore()).isEqualTo(64.0).isEqualTo(noWeather.totalScore());
    }

    @Test
    void cWeightMustBePositive() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ScoreModel(1, new FacilityWeights(1, 1, 5), 50, 300,
                0.1, PiecewiseScale.parse("0:0,40:100"), 1.0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
