package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.WeatherInfo;

import java.util.List;

/**
 * 점수가 매겨진 후보 경로 한 개 (응답 항목).
 *
 * @param coordinates  {@code [위도, 경도]} 목록
 * @param totalScore   총점 0~100. 설계 파라미터 기반 상대 비교 값이며 위험도가 아니다.
 * @param breakdown    축별 점수와 계산 입력값(날씨를 얻지 못하면 C축은 빠진다)
 * @param riskSections 경로 반경 이내 급경사지(붕괴위험 지정지). 점수에는 섞지 않는 경고 레이어이며 등급은 없다.
 * @param weather      경로 중간 지점 기준 날씨. {@code fallback=true} 면 실제 값이 아니고 점수에 반영되지 않았다.
 * @param recommended  후보 중 총점이 가장 높은 경로(동점이면 소요 시간이 짧은 쪽)
 */
public record ScoredRoute(
        String routeId,
        List<double[]> coordinates,
        int totalDistance,
        int totalTime,
        int searchOptionCode,
        String searchOption,
        double totalScore,
        List<AxisScore> breakdown,
        int cctvCount,
        int lightCount,
        int policeCount,
        List<RiskSection> riskSections,
        WeatherInfo weather,
        boolean recommended) {

    ScoredRoute asRecommended() {
        return new ScoredRoute(routeId, coordinates, totalDistance, totalTime, searchOptionCode, searchOption,
                totalScore, breakdown, cctvCount, lightCount, policeCount, riskSections, weather, true);
    }
}
