package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.density.DensityCell;
import com.flatmap.navigation.density.FacilityDensityGridService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시설 밀도 격자. 조회(GET)는 공개, 재계산(POST)은 관리용(X-Admin-Api-Key)이다.
 */
@RestController
@RequestMapping("/api/density-grid")
public class DensityGridController {

    private static final Logger log = LoggerFactory.getLogger(DensityGridController.class);

    static final String NOTE =
            "격자 안 안전시설(CCTV·보안등·경찰시설)의 밀도를 설정된 가중치·구간으로 환산한 설계 파라미터 값입니다. "
                    + "시설 밀도가 낮다는 것은 위험하다는 뜻이 아니라 수집된 시설 데이터가 적다는 뜻입니다.";

    private final FacilityDensityGridService service;

    public DensityGridController(FacilityDensityGridService service) {
        this.service = service;
    }

    /** 전체 격자. minLat/minLng/maxLat/maxLng 를 모두 주면 그 사각형과 겹치는 셀만 돌려준다. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getGrid(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double maxLng) {

        boolean anyBound = minLat != null || minLng != null || maxLat != null || maxLng != null;
        List<DensityCell> cells;
        if (!anyBound) {
            cells = service.findCells();
        } else {
            boolean allBounds = minLat != null && minLng != null && maxLat != null && maxLng != null;
            if (!allBounds || !(minLat < maxLat) || !(minLng < maxLng)) {
                return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_BOUNDS", "조회 범위가 올바르지 않습니다.");
            }
            cells = service.findCellsIntersecting(minLat, minLng, maxLat, maxLng);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("note", NOTE);
        body.put("count", cells.size());
        body.put("cells", cells);
        return ResponseEntity.ok(body);
    }

    /** 관리용: 현재 시설 데이터로 격자를 다시 만든다. 실패하면 기존 격자는 그대로다. */
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate() {
        try {
            FacilityDensityGridService.RecalcResult result = service.recalculate();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("totalCells", result.totalCells());
            body.put("byLevel", result.byLevel());
            body.put("scoreVersion", result.scoreVersion());
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            log.error("시설 밀도 격자 재계산 실패", e);
            return ApiErrors.internal();
        }
    }
}
