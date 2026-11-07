package com.flatmap.navigation.controller;

import com.flatmap.navigation.dto.*;
import com.flatmap.navigation.service.DetourService;
import com.flatmap.navigation.service.SteepSlopeService;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/route")
public class WalkRouteController {

    private static final Logger log = LoggerFactory.getLogger(WalkRouteController.class);
    private final TMapRouteService tMapRouteService;
    private final SteepSlopeService steepSlopeService;
    private final WeatherService weatherService;
    private final DetourService detourService;

    public WalkRouteController(TMapRouteService tMapRouteService,
                               SteepSlopeService steepSlopeService,
                               WeatherService weatherService,
                               DetourService detourService) {
        this.tMapRouteService = tMapRouteService;
        this.steepSlopeService = steepSlopeService;
        this.weatherService = weatherService;
        this.detourService = detourService;
    }

    @PostMapping("/walk")
    public ResponseEntity<RouteResponse> getWalkingRoute(@RequestBody RouteRequest request) {
        log.info("도보 경로 요청: origin=({},{}), dest=({},{}), option={}",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getOption());

        try {
            RouteResponse response = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption()
            );

            // 1. 경로 좌표와 DB의 급경사지 데이터를 비교하여 위험구간 판별
            List<RiskSection> riskSections = steepSlopeService.findRiskSections(response.getCoords());

            // 2. 날씨 조회 (경로 중간 지점 기준)
            List<RouteResponse.Coord> coords = response.getCoords();
            RouteResponse.Coord midPoint = coords.get(coords.size() / 2);
            WeatherInfo weather = weatherService.getWeather(midPoint.getLat(), midPoint.getLng());
            response.setWeather(weather);

            // 3. 날씨 기반 위험도 보정
            steepSlopeService.adjustRiskByWeather(riskSections, weather);
            response.setRiskSections(riskSections);

            if (!riskSections.isEmpty()) {
                log.info("경로에서 위험구간 {}건 감지 (날씨 보정 적용)", riskSections.size());
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("도보 경로 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/walk/detour")
    public ResponseEntity<RouteResponse> getDetourRoute(@RequestBody DetourRequest request) {
        log.info("우회 경로 요청: origin=({},{}), dest=({},{}), 회피지점 {}개",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getAvoidPoints() != null ? request.getAvoidPoints().size() : 0);

        try {
            // 1. 원래 경로를 먼저 가져와서 경로 좌표 확보 (경유지 계산용)
            RouteResponse originalRoute = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption()
            );

            // 2. 원래 경로의 위험구간 분석 (우회 효과 비교 기준)
            List<RiskSection> originalRisks = steepSlopeService.findRiskSections(originalRoute.getCoords());

            // 3. avoidPoints → RiskSection 변환 후 passList 생성
            List<RiskSection> fakeRisks = request.getAvoidPoints().stream()
                    .map(ap -> new RiskSection(ap.getLat(), ap.getLng(), "avoid",
                            0, ap.getRiskLevel(), 0, ap.getNearestRouteIdx()))
                    .collect(Collectors.toList());

            String passList = detourService.generatePassList(fakeRisks, originalRoute.getCoords());

            if (passList == null || passList.isEmpty()) {
                log.warn("우회 경유지를 생성할 수 없음 — 원래 경로 반환");
                return ResponseEntity.ok(buildFallback(originalRoute, originalRisks,
                        "우회 가능한 안전 지점을 찾지 못해 원래 경로를 반환했습니다."));
            }

            // 4. 경유지 포함 우회 경로 검색
            RouteResponse detourResponse = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption(), passList
            );

            // 5. 우회 경로에 대해서도 위험구간 분석
            List<RiskSection> detourRisks = steepSlopeService.findRiskSections(detourResponse.getCoords());

            // 6. 우회 경로가 원래 경로보다 위험구간을 줄이지 못했다면 원래 경로를 반환한다.
            //    (급경사지 밀집 지역에서는 우회가 다른 위험구간에 다시 걸려 오히려 악화될 수 있음)
            if (detourRisks.size() >= originalRisks.size()) {
                log.warn("우회 경로가 위험구간을 줄이지 못함 (원래 {}건 → 우회 {}건) — 원래 경로 반환",
                        originalRisks.size(), detourRisks.size());
                return ResponseEntity.ok(buildFallback(originalRoute, originalRisks,
                        "더 안전한 우회 경로를 찾지 못해 원래 경로를 반환했습니다."));
            }

            // 7. 날씨 조회 + 보정
            List<RouteResponse.Coord> coords = detourResponse.getCoords();
            RouteResponse.Coord midPoint = coords.get(coords.size() / 2);
            WeatherInfo weather = weatherService.getWeather(midPoint.getLat(), midPoint.getLng());
            detourResponse.setWeather(weather);
            steepSlopeService.adjustRiskByWeather(detourRisks, weather);
            detourResponse.setRiskSections(detourRisks);
            detourResponse.setDetourApplied(true);

            log.info("우회 경로 검색 완료: 거리={}m, 시간={}초, 위험구간={}건 (원래 {}건에서 개선)",
                    detourResponse.getTotalDistance(), detourResponse.getTotalDuration(),
                    detourRisks.size(), originalRisks.size());

            return ResponseEntity.ok(detourResponse);
        } catch (RuntimeException e) {
            log.error("우회 경로 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 우회에 실패했거나 우회가 더 낫지 않을 때, 원래 경로에 날씨·위험구간을 채워 반환용으로 만든다.
     */
    private RouteResponse buildFallback(RouteResponse originalRoute,
                                        List<RiskSection> originalRisks,
                                        String message) {
        List<RouteResponse.Coord> coords = originalRoute.getCoords();
        RouteResponse.Coord midPoint = coords.get(coords.size() / 2);
        WeatherInfo weather = weatherService.getWeather(midPoint.getLat(), midPoint.getLng());
        originalRoute.setWeather(weather);
        steepSlopeService.adjustRiskByWeather(originalRisks, weather);
        originalRoute.setRiskSections(originalRisks);
        originalRoute.setDetourApplied(false);
        originalRoute.setDetourMessage(message);
        return originalRoute;
    }
}
