package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.dto.DetourRequest;
import com.flatmap.navigation.dto.RouteRequest;
import com.flatmap.navigation.service.DetourRouteService;
import com.flatmap.navigation.service.RouteProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단일 도보 경로와 우회 경로. 응답에는 급경사지 경고 구간·날씨·축별 점수(breakdown)가 들어 있다.
 * 경로 제공자(T Map) 실패는 외부 메시지 없이 일반화된 502 로만 알린다.
 */
@RestController
@RequestMapping("/api/route")
public class WalkRouteController {

    private static final Logger log = LoggerFactory.getLogger(WalkRouteController.class);

    private final DetourRouteService routeService;

    public WalkRouteController(DetourRouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/walk")
    public ResponseEntity<?> getWalkingRoute(@RequestBody RouteRequest request) {
        log.info("도보 경로 요청: origin=({},{}), dest=({},{}), option={}",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getOption());
        try {
            return ResponseEntity.ok(routeService.walk(request));
        } catch (RouteProviderException e) {
            log.error("도보 경로 검색 실패: {}", e.getMessage());
            return providerUnavailable();
        }
    }

    @PostMapping("/walk/detour")
    public ResponseEntity<?> getDetourRoute(@RequestBody DetourRequest request) {
        log.info("우회 경로 요청: origin=({},{}), dest=({},{}), 회피지점 {}개",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getAvoidPoints() != null ? request.getAvoidPoints().size() : 0);
        try {
            return ResponseEntity.ok(routeService.detour(request));
        } catch (RouteProviderException e) {
            log.error("우회 경로 검색 실패: {}", e.getMessage());
            return providerUnavailable();
        }
    }

    private static ResponseEntity<?> providerUnavailable() {
        return ApiErrors.body(HttpStatus.BAD_GATEWAY, "ROUTE_PROVIDER_UNAVAILABLE",
                "경로를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
