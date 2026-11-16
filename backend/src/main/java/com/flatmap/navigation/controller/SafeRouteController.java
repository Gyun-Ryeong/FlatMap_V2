package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.routescore.ScoreModel;
import com.flatmap.navigation.routescore.SafeRouteService;
import com.flatmap.navigation.routescore.ScoredRoute;
import com.flatmap.navigation.walk.InvalidWalkModeException;
import com.flatmap.navigation.walk.WalkMode;
import com.flatmap.navigation.walk.WalkModel;
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
 * 후보 경로 3종 + 경로별 점수(A축 야간 시설 노출, B축 보행난이도, C축 날씨 보정). 조회(GET)라 공개다.
 */
@RestController
@RequestMapping("/api/routes")
public class SafeRouteController {

    static final String SCORE_NOTE =
            "총점은 설정된 가중치·구간으로 계산한 설계 파라미터 기반의 상대 비교 값이며, 검증된 위험도나 안전도가 아닙니다. "
                    + "A축(야간 시설 노출), B축(보행난이도, 보행 유형별 경사 기준), C축(날씨 보정)을 반영합니다. "
                    + "기상 정보를 가져오지 못했거나 경사 데이터가 없는 경로는 해당 축을 빼고 계산합니다. "
                    + "급경사지(붕괴위험 지정지)는 점수에 반영하지 않는 별도 경고입니다. "
                    + WalkModel.LIMITATION;

    private final SafeRouteService safeRouteService;
    private final ScoreModel scoreModel;
    private final WalkModel walkModel;

    public SafeRouteController(SafeRouteService safeRouteService, ScoreModel scoreModel, WalkModel walkModel) {
        this.safeRouteService = safeRouteService;
        this.scoreModel = scoreModel;
        this.walkModel = walkModel;
    }

    @GetMapping("/safe")
    public ResponseEntity<Map<String, Object>> getSafeRoutes(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double endLat,
            @RequestParam double endLng,
            @RequestParam(required = false) String mode) {

        if (!isValidKoreaLat(startLat) || !isValidKoreaLat(endLat)
                || !isValidKoreaLng(startLng) || !isValidKoreaLng(endLng)) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES", "좌표가 올바르지 않습니다.");
        }

        WalkMode walkMode;
        try {
            walkMode = WalkMode.parse(mode, safeRouteService.defaultWalkMode());
        } catch (InvalidWalkModeException e) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_WALK_MODE", "보행 유형이 올바르지 않습니다.");
        }

        List<ScoredRoute> routes = safeRouteService.findScoredRoutes(startLat, startLng, endLat, endLng, walkMode);
        if (routes.isEmpty()) {
            return ApiErrors.body(HttpStatus.BAD_GATEWAY, "ROUTE_PROVIDER_UNAVAILABLE",
                    "경로를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routes", routes);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("version", scoreModel.version());
        model.put("kind", "DESIGN_PARAMETER");
        model.put("axes", List.of("A", "B", "C"));
        model.put("walkMode", walkMode);
        model.put("walkThresholds", walkThresholds());
        model.put("note", SCORE_NOTE);
        body.put("scoreModel", model);
        return ResponseEntity.ok(body);
    }

    /** 보행 유형별 경사 임계값(각도 °). 화면 안내가 서버 설정과 어긋나지 않게 응답으로 내려 준다. */
    private Map<String, Object> walkThresholds() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (WalkMode m : WalkMode.values()) {
            WalkModel.Thresholds t = walkModel.thresholds(m);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("goodMaxDeg", t.goodMaxDeg());
            one.put("cautionMaxDeg", t.cautionMaxDeg());
            out.put(m.name(), one);
        }
        return out;
    }

    private static boolean isValidKoreaLat(double lat) {
        return lat >= 33 && lat <= 43;
    }

    private static boolean isValidKoreaLng(double lng) {
        return lng >= 124 && lng <= 132;
    }
}
