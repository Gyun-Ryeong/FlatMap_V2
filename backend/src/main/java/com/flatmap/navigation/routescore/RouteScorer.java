package com.flatmap.navigation.routescore;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 경로 점수 계산(순수 함수 — DB 에 접근하지 않는다).
 *
 * <p>A축(야간 시설 노출) = 구간 함수( 가중 시설 수 ÷ 경로 길이(km) ).
 * 총점은 반영된 축 점수의 가중 평균이며, 지금은 A축 하나뿐이다.
 * 이 점수는 설계 파라미터에서 나온 상대 비교 값이고 과학적 위험도·안전도가 아니다.
 */
@Component
public class RouteScorer {

    public static final String AXIS_A = "A";
    public static final String AXIS_A_NAME = "야간 시설 노출";

    private final ScoreModel model;

    public RouteScorer(ScoreModel model) {
        this.model = model;
    }

    public RouteScore score(RouteExposure exposure) {
        List<AxisScore> axes = List.of(axisA(exposure));
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

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
