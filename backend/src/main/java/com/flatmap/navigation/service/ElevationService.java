package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElevationService {

    private static final Logger log = LoggerFactory.getLogger(ElevationService.class);
    private static final String ELEVATIONS_JSON = "dem/elevations.json";

    private final SteepSlopeAreaRepository repository;
    private final ObjectMapper objectMapper;

    public ElevationService(SteepSlopeAreaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * dem/elevations.json (Node 스크립트로 추출한 Copernicus DEM 고도값) 기반으로
     * 급경사지 경사도와 위험도를 업데이트한다.
     * 복구: POST /api/steep-slope/update-grade/csv
     */
    public Map<String, Object> updateGradesFromElevation() throws Exception {
        File jsonFile = new File(ELEVATIONS_JSON);
        if (!jsonFile.exists()) {
            throw new RuntimeException("elevations.json 파일 없음: " + jsonFile.getAbsolutePath()
                    + " — backend/dem/extract_elevations.js 를 먼저 실행하세요.");
        }

        // id → angleDeg 매핑 구성
        JsonNode root = objectMapper.readTree(jsonFile);
        Map<Long, Double> angleMap = new HashMap<>();
        for (JsonNode node : root) {
            if (node.hasNonNull("angleDeg")) {
                angleMap.put(node.path("id").asLong(), node.path("angleDeg").asDouble());
            }
        }
        log.info("elevations.json 로드: {}건 (유효 고도값 {}건)", root.size(), angleMap.size());

        List<SteepSlopeArea> areas = repository.findAll();
        int updated = 0;
        int skipped = 0;

        for (SteepSlopeArea area : areas) {
            Double angleDeg = angleMap.get(area.getId());
            if (angleDeg == null) {
                log.warn("[{}] elevations.json에 없음, 스킵", area.getName());
                skipped++;
                continue;
            }

            // 붕괴위험 지정지에는 보행 위험등급을 부여하지 않는다. 경사도(°)만 갱신한다.
            area.setGrade(angleDeg);
            updated++;
        }

        repository.saveAll(areas);
        log.info("Copernicus DEM 기반 업데이트 완료 — 업데이트: {}, 스킵: {}", updated, skipped);
        return Map.of("total", areas.size(), "updated", updated, "skipped", skipped,
                "message", "Copernicus DEM (30m) 기반 경사도 업데이트 완료");
    }
}