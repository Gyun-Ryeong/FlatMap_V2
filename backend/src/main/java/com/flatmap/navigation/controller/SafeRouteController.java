package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.routescore.ScoreModel;
import com.flatmap.navigation.routescore.SafeRouteService;
import com.flatmap.navigation.routescore.ScoredRoute;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 후보 경로 3종 + 경로별 점수(A축 야간 시설 노출). 조회(GET)라 공개다.
 */
@RestController
@RequestMapping("/api/routes")
public class SafeRouteController {

    static final String SCORE_NOTE =
            "총점은 설정된 가중치·구간으로 계산한 설계 파라미터 기반의 상대 비교 값이며, 검증된 위험도나 안전도가 아닙니다. "
                    + "현재는 A축(야간 시설 노출)만 반영하고, 보행난이도(B)와 날씨(C)는 아직 반영하지 않습니다.";

    private final SafeRouteService safeRouteService;
    private final ScoreModel scoreModel;

    public SafeRouteController(SafeRouteService safeRouteService, ScoreModel scoreModel) {
        this.safeRouteService = safeRouteService;
        this.scoreModel = scoreModel;
    }

    @GetMapping("/safe")
    public ResponseEntity<Map<String, Object>> getSafeRoutes(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double endLat,
            @RequestParam double endLng) {

        if (!isValidKoreaLat(startLat) || !isValidKoreaLat(endLat)
                || !isValidKoreaLng(startLng) || !isValidKoreaLng(endLng)) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES", "좌표가 올바르지 않습니다.");
        }

        List<ScoredRoute> routes = safeRouteService.findScoredRoutes(startLat, startLng, endLat, endLng);
        if (routes.isEmpty()) {
            return ApiErrors.body(HttpStatus.BAD_GATEWAY, "ROUTE_PROVIDER_UNAVAILABLE",
                    "경로를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routes", routes);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("version", scoreModel.version());
        model.put("kind", "DESIGN_PARAMETER");
        model.put("axes", List.of("A"));
        model.put("note", SCORE_NOTE);
        body.put("scoreModel", model);
        return ResponseEntity.ok(body);
    }

    private static boolean isValidKoreaLat(double lat) {
        return lat >= 33 && lat <= 43;
    }

    private static boolean isValidKoreaLng(double lng) {
        return lng >= 124 && lng <= 132;
    }
}
