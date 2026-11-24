package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import com.flatmap.navigation.service.TMapRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 후보 경로 3종을 받아 경로마다 A축(야간 시설 노출) 점수를 매긴다.
 * 경로 하나당 시설 집계 쿼리는 정확히 한 번이다(샘플 점마다 묻지 않는다).
 */
@Service
public class SafeRouteService {

    private static final Logger log = LoggerFactory.getLogger(SafeRouteService.class);

    private final TMapRouteService tmap;
    private final SafetyFacilityRepository facilities;
    private final RouteScorer scorer;
    private final ScoreModel model;

    public SafeRouteService(TMapRouteService tmap, SafetyFacilityRepository facilities,
                            RouteScorer scorer, ScoreModel model) {
        this.tmap = tmap;
        this.facilities = facilities;
        this.scorer = scorer;
        this.model = model;
    }

    /** 후보 경로를 모두 얻지 못하면(모두 실패) 빈 목록. 일부만 성공해도 그 경로들로 진행한다. */
    public List<ScoredRoute> findScoredRoutes(double startLat, double startLng, double endLat, double endLng) {
        List<TMapRouteService.CandidateRoute> candidates =
                tmap.getCandidateRoutes(startLat, startLng, endLat, endLng);

        List<ScoredRoute> scored = new ArrayList<>(candidates.size());
        for (TMapRouteService.CandidateRoute candidate : candidates) {
            scored.add(score(candidate));
        }
        return markRecommended(scored);
    }

    private ScoredRoute score(TMapRouteService.CandidateRoute candidate) {
        RouteResponse route = candidate.route();
        List<double[]> coordinates = new ArrayList<>(route.getCoords().size());
        for (RouteResponse.Coord c : route.getCoords()) {
            coordinates.add(new double[]{c.getLat(), c.getLng()});
        }

        RouteExposure exposure = facilities.countAlongRoute(
                RouteGeometry.toWkt(coordinates), model.radiusMeters(), model.policeRadiusMeters());
        RouteScore score = scorer.score(exposure);
        log.info("경로 점수 계산: searchOption={}, 총점={}, CCTV={}, 보안등={}, 경찰={}",
                candidate.searchOption(), score.totalScore(),
                exposure.cctvCount(), exposure.lightCount(), exposure.policeCount());

        return new ScoredRoute(
                UUID.randomUUID().toString(),
                coordinates,
                route.getTotalDistance(),
                route.getTotalDuration(),
                candidate.searchOption(),
                candidate.name(),
                score.totalScore(),
                score.breakdown(),
                (int) exposure.cctvCount(),
                (int) exposure.lightCount(),
                (int) exposure.policeCount(),
                false);
    }

    /** 총점이 가장 높은 경로를 추천으로 표시한다. 동점이면 소요 시간이 짧은 쪽, 그래도 같으면 앞의 후보. */
    static List<ScoredRoute> markRecommended(List<ScoredRoute> routes) {
        if (routes.isEmpty()) return routes;
        ScoredRoute best = routes.stream()
                .min(Comparator.comparingDouble((ScoredRoute r) -> -r.totalScore())
                        .thenComparingInt(ScoredRoute::totalTime))
                .orElseThrow();
        List<ScoredRoute> result = new ArrayList<>(routes.size());
        for (ScoredRoute r : routes) {
            result.add(r == best ? r.asRecommended() : r);
        }
        return result;
    }
}
