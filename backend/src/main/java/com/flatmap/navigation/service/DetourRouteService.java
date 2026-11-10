package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.DetourRequest;
import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteRequest;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.routescore.SafeRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 단일 경로 조회와 우회 경로 조회(경로 → 급경사지 구간 → 점수 → 우회 채택 판단).
 *
 * <p>우회 경로는 아래를 <b>모두</b> 만족할 때만 채택하고, 아니면 원경로를 돌려준다(detourApplied=false).
 * <ol>
 *   <li>우회 경유지를 만들 수 있다.</li>
 *   <li>T Map 이 경유지 포함 경로를 돌려준다(실패하면 원경로).</li>
 *   <li>경로 반경 안 급경사지(붕괴위험 경고 레이어) 구간 수가 원경로보다 <b>줄었다</b>.</li>
 *   <li>총점(A·C축)이 원경로보다 {@code detour.max-score-drop} 점 넘게 떨어지지 않았다.</li>
 * </ol>
 * 급경사지는 점수에 섞지 않는다 — 우회의 목적(경고 구간 회피)과 점수(시설 노출·날씨)는 별개의 축이고,
 * 이 규칙이 둘이 서로 어긋나는 우회를 걸러낸다. 응답에는 항상 축별 breakdown 이 들어 있다.
 *
 * <p>원경로 조회에 실패하면 {@link RouteProviderException} 이 그대로 올라간다(컨트롤러가 일반화된 502 로 바꾼다).
 */
@Service
public class DetourRouteService {

    private static final Logger log = LoggerFactory.getLogger(DetourRouteService.class);

    private final TMapRouteService tmap;
    private final SteepSlopeService steepSlopeService;
    private final DetourService detourService;
    private final SafeRouteService safeRouteService;
    private final double maxScoreDrop;

    public DetourRouteService(TMapRouteService tmap, SteepSlopeService steepSlopeService,
                              DetourService detourService, SafeRouteService safeRouteService,
                              @Value("${detour.max-score-drop}") double maxScoreDrop) {
        if (maxScoreDrop < 0) {
            throw new IllegalArgumentException("detour.max-score-drop 은 0 이상이어야 한다.");
        }
        this.tmap = tmap;
        this.steepSlopeService = steepSlopeService;
        this.detourService = detourService;
        this.safeRouteService = safeRouteService;
        this.maxScoreDrop = maxScoreDrop;
    }

    /** 도보 경로 한 개 + 급경사지 구간 + 점수·날씨. */
    public RouteResponse walk(RouteRequest request) {
        RouteResponse route = tmap.getWalkingRoute(
                request.getOriginLng(), request.getOriginLat(),
                request.getDestLng(), request.getDestLat(), request.getOption());

        List<RiskSection> risks = steepSlopeService.findRiskSections(route.getCoords());
        if (!risks.isEmpty()) {
            log.info("경로에서 붕괴위험 지정지 {}건 감지", risks.size());
        }
        fill(route, risks, safeRouteService.evaluate(toLatLng(route)));
        return route;
    }

    public RouteResponse detour(DetourRequest request) {
        // 1. 원래 경로 (경유지 계산과 우회 효과 비교의 기준)
        RouteResponse original = tmap.getWalkingRoute(
                request.getOriginLng(), request.getOriginLat(),
                request.getDestLng(), request.getDestLat(), request.getOption());
        List<RiskSection> originalRisks = steepSlopeService.findRiskSections(original.getCoords());
        SafeRouteService.Evaluation originalEval = safeRouteService.evaluate(toLatLng(original));

        // 2. avoidPoints → 경유지
        List<RiskSection> avoid = new ArrayList<>();
        if (request.getAvoidPoints() != null) {
            for (DetourRequest.RiskCoord ap : request.getAvoidPoints()) {
                avoid.add(new RiskSection(ap.getLat(), ap.getLng(), "avoid", null, null, 0, ap.getNearestRouteIdx()));
            }
        }
        String passList = detourService.generatePassList(avoid, original.getCoords());
        if (passList == null || passList.isEmpty()) {
            log.warn("우회 경유지를 생성할 수 없음 — 원래 경로 반환");
            return original(original, originalRisks, originalEval,
                    "우회 가능한 안전 지점을 찾지 못해 원래 경로를 반환했습니다.");
        }

        // 3. 경유지 포함 경로 (T Map 실패는 원경로로 대신한다)
        RouteResponse detour;
        try {
            detour = tmap.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(), request.getOption(), passList);
        } catch (RouteProviderException e) {
            log.warn("우회 경로 조회 실패 — 원래 경로 반환: {}", e.getMessage());
            return original(original, originalRisks, originalEval,
                    "우회 경로를 가져오지 못해 원래 경로를 반환했습니다.");
        }

        // 4. 급경사지 구간이 줄어야 한다 (급경사지 밀집 지역에서는 우회가 다른 구간에 다시 걸려 악화될 수 있다)
        List<RiskSection> detourRisks = steepSlopeService.findRiskSections(detour.getCoords());
        if (detourRisks.size() >= originalRisks.size()) {
            log.warn("우회 경로가 위험구간을 줄이지 못함 (원래 {}건 → 우회 {}건) — 원래 경로 반환",
                    originalRisks.size(), detourRisks.size());
            return original(original, originalRisks, originalEval,
                    "더 안전한 우회 경로를 찾지 못해 원래 경로를 반환했습니다.");
        }

        // 5. 점수가 허용 폭 넘게 떨어지지 않아야 한다
        SafeRouteService.Evaluation detourEval = safeRouteService.evaluate(toLatLng(detour));
        double drop = originalEval.score().totalScore() - detourEval.score().totalScore();
        if (drop > maxScoreDrop) {
            log.warn("우회 경로 점수가 허용 폭 넘게 낮음 ({} → {}, 허용 {}) — 원래 경로 반환",
                    originalEval.score().totalScore(), detourEval.score().totalScore(), maxScoreDrop);
            return original(original, originalRisks, originalEval,
                    "우회 경로는 붕괴위험 지정지를 줄이지만 점수가 크게 낮아져 원래 경로를 반환했습니다.");
        }

        fill(detour, detourRisks, detourEval);
        detour.setOriginalTotalScore(originalEval.score().totalScore());
        detour.setDetourApplied(true);
        log.info("우회 경로 검색 완료: 거리={}m, 시간={}초, 위험구간={}건 (원래 {}건), 점수 {} (원래 {})",
                detour.getTotalDistance(), detour.getTotalDuration(), detourRisks.size(), originalRisks.size(),
                detourEval.score().totalScore(), originalEval.score().totalScore());
        return detour;
    }

    /** 우회하지 않고 원경로를 돌려줄 때의 응답. */
    private RouteResponse original(RouteResponse route, List<RiskSection> risks,
                                   SafeRouteService.Evaluation eval, String message) {
        fill(route, risks, eval);
        route.setOriginalTotalScore(eval.score().totalScore());
        route.setDetourApplied(false);
        route.setDetourMessage(message);
        return route;
    }

    private static void fill(RouteResponse route, List<RiskSection> risks, SafeRouteService.Evaluation eval) {
        route.setRiskSections(risks);
        route.setWeather(eval.weather());
        route.setTotalScore(eval.score().totalScore());
        route.setBreakdown(eval.score().breakdown());
    }

    private static List<double[]> toLatLng(RouteResponse route) {
        List<double[]> points = new ArrayList<>(route.getCoords().size());
        for (RouteResponse.Coord c : route.getCoords()) {
            points.add(new double[]{c.getLat(), c.getLng()});
        }
        return points;
    }
}
