package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.walk.DemException;
import com.flatmap.navigation.walk.WalkGridBuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 보행난이도(B축) 경사 격자 관리. 관리용이다(X-Admin-Api-Key).
 * DEM 파일 위치는 요청으로 받지 않는다 — 서버 설정(walk.dem.path)에서만 읽는다.
 */
@RestController
@RequestMapping("/api/walk-difficulty")
public class WalkDifficultyController {

    private static final Logger log = LoggerFactory.getLogger(WalkDifficultyController.class);

    private final WalkGridBuildService buildService;

    public WalkDifficultyController(WalkGridBuildService buildService) {
        this.buildService = buildService;
    }

    /** DEM 에서 경사 격자를 다시 계산해 교체한다. 실패하면 기존 격자는 그대로다. */
    @PostMapping("/grid/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        try {
            WalkGridBuildService.BuildResult r = buildService.rebuild();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("gridCells", r.gridCells());
            body.put("loadedCells", r.loadedCells());
            body.put("elapsedMillis", r.elapsedMillis());
            return ResponseEntity.ok(body);
        } catch (WalkGridBuildService.DemMissingException e) {
            return ApiErrors.body(HttpStatus.CONFLICT, "DEM_NOT_FOUND",
                    "DEM 원본 파일을 찾을 수 없습니다. 서버 설정(walk.dem.path)을 확인해 주세요.");
        } catch (WalkGridBuildService.BuildInProgressException e) {
            return ApiErrors.body(HttpStatus.CONFLICT, "BUILD_IN_PROGRESS", "격자 적재가 이미 진행 중입니다.");
        } catch (DemException e) {
            log.error("경사 격자 적재 실패(DEM): {}", e.getMessage());
            return ApiErrors.body(HttpStatus.UNPROCESSABLE_ENTITY, "DEM_UNSUPPORTED",
                    "DEM 파일을 읽을 수 없거나 지원하지 않는 형식입니다.");
        } catch (RuntimeException e) {
            log.error("경사 격자 적재 실패", e);
            return ApiErrors.internal();
        }
    }
}
