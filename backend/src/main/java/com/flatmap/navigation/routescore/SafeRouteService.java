package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import com.flatmap.navigation.service.SteepSlopeService;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 후보 경로 3종을 받아 경로마다 A축(야간 시설 노출)과 C축(날씨 보정) 점수를 매긴다.
 * 경로 하나당 시설 집계 쿼리는 정확히 한 번이다(급경사지 경고 구간 조회는 별도 한 번이며 점수에 섞지 않는다)(샘플 점마다 묻지 않는다). 날씨는 격자 캐시를 거치므로
 * 같은 격자의 후보끼리는 외부 호출이 한 번이다. 날씨를 얻지 못하면(fallback) C축 없이 A축만으로 점수를 낸다.
 */
@Service
public class SafeRouteService {

    private static final Logger log = LoggerFactory.getLogger(SafeRouteService.class);

    private final TMapRouteService tmap;
    private final SafetyFacilityRepository facilities;
    private final RouteScorer scorer;
    private final ScoreModel model;
    private final WeatherService weatherService;
    private final SteepSlopeService steepSlopeService;

    public SafeRouteService(TMapRouteService tmap, SafetyFacilityRepository facilities,
                            RouteScorer scorer, ScoreModel model, WeatherService weatherService,
                            SteepSlopeService steepSlopeService) {
        this.tmap = tmap;
        this.facilities = facilities;
        this.scorer = scorer;
        this.model = model;
        this.weatherService = weatherService;
        this.steepSlopeService = steepSlopeService;
    }

    /** 경로 하나의 평가 결과: 점수(축별 내역 포함), 시설 집계, 점수에 쓴 날씨. */
    public record Evaluation(RouteScore score, RouteExposure exposure, WeatherInfo weather) {
    }

    /**
     * 경로 좌표({@code [위도, 경도]})의 점수를 계산한다 — 시설 집계 쿼리 1회 + 경로 중간 지점 날씨(캐시).
     * 우회 경로 통합(DetourRouteService)도 같은 함수를 쓴다.
     */
    public Evaluation evaluate(List<double[]> coordinates) {
        RouteExposure exposure = facilities.countAlongRoute(
                RouteGeometry.toWkt(coordinates), model.radiusMeters(), model.policeRadiusMeters());
        double[] mid = coordinates.get(coordinates.size() / 2);
        WeatherInfo weather = weatherService.getWeather(mid[0], mid[1]);
        return new Evaluation(scorer.score(exposure, weather), exposure, weather);
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

        Evaluation evaluation = evaluate(coordinates);
        RouteExposure exposure = evaluation.exposure();
        RouteScore score = evaluation.score();
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
                steepSlopeService.findRiskSections(route.getCoords()),
                evaluation.weather(),
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
