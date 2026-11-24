package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.walk.WalkAssessment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 경로 점수 계산(순수 함수 — DB 에 접근하지 않는다).
 *
 * <p>A축(야간 시설 노출) = 구간 함수( 가중 시설 수 ÷ 경로 길이(km) ).
 * C축(날씨 보정) = 100 ÷ 날씨 보정 계수 — 실제 기상 값이 있을 때만 반영한다.
 * 기상 API 실패로 대체값(fallback)이 온 경우에는 C축을 넣지 않는다(0점이나 100점으로 채우지 않는다).
 * B축(보행난이도) = 경로 경사 샘플의 양호/주의/위험 비율에서 나온 점수 — 경사 격자에 값이 충분할 때만 반영한다
 * (평가가 없으면 B축은 breakdown 에 넣지 않는다).
 * 총점은 반영된 축 점수의 가중 평균이다.
 * 이 점수는 설계 파라미터에서 나온 상대 비교 값이고 과학적 위험도·안전도가 아니다.
 */
@Component
public class RouteScorer {

    public static final String AXIS_A = "A";
    public static final String AXIS_A_NAME = "야간 시설 노출";

    public static final String AXIS_B = "B";
    public static final String AXIS_B_NAME = "보행난이도";

    public static final String AXIS_C = "C";
    public static final String AXIS_C_NAME = "날씨 보정";

    private final ScoreModel model;
    private final WeatherCorrection weatherCorrection;

    public RouteScorer(ScoreModel model, WeatherCorrection weatherCorrection) {
        this.model = model;
        this.weatherCorrection = weatherCorrection;
    }

    /** A축만 반영한 점수(날씨 없음). */
    public RouteScore score(RouteExposure exposure) {
        return score(exposure, null);
    }

    /** weather 가 null 이거나 fallback 이면 C축 없이 A축만 반영한다. */
    public RouteScore score(RouteExposure exposure, WeatherInfo weather) {
        return score(exposure, weather, null);
    }

    /** walk 가 null 이면(경사 데이터 없음) B축 없이 계산한다. */
    public RouteScore score(RouteExposure exposure, WeatherInfo weather, WalkAssessment walk) {
        List<AxisScore> axes = new ArrayList<>();
        axes.add(axisA(exposure));
        if (walk != null) {
            axes.add(axisB(walk));
        }
        if (weather != null && !weather.isFallback()) {
            axes.add(axisC(weather));
        }
        double weightSum = 0;
        double weighted = 0;
        for (AxisScore a : axes) {
            weightSum += a.weight();
            weighted += a.score() * a.weight();
        }
        return new RouteScore(round1(weighted / weightSum), axes);
    }

    AxisScore axisA(RouteExposure e) {
        double weighted = model.weights().weighted(e.cctvCount(), e.lightCount(), e.policeCount());
        double lengthKm = Math.max(e.lengthMeters() / 1000.0, model.minRouteKm());
        double perKm = weighted / lengthKm;
        double score = round1(model.exposureScale().apply(perKm));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cctvCount", e.cctvCount());
        detail.put("lightCount", e.lightCount());
        detail.put("policeCount", e.policeCount());
        detail.put("weightedCount", round2(weighted));
        detail.put("routeLengthMeters", round1(e.lengthMeters()));
        detail.put("weightedPerKm", round2(perKm));
        detail.put("radiusMeters", model.radiusMeters());
        detail.put("policeRadiusMeters", model.policeRadiusMeters());
        return new AxisScore(AXIS_A, AXIS_A_NAME, score, model.axisWeightA(), detail);
    }

    AxisScore axisB(WalkAssessment w) {
        return new AxisScore(AXIS_B, AXIS_B_NAME, w.score(), model.axisWeightB(), w.detail());
    }

    AxisScore axisC(WeatherInfo w) {
        WeatherCorrection.Applied applied = weatherCorrection.apply(w);
        double score = round1(weatherCorrection.score(applied.multiplier()));
        return new AxisScore(AXIS_C, AXIS_C_NAME, score, model.axisWeightC(),
                weatherCorrection.detail(w, applied));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
