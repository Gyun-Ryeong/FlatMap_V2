package com.flatmap.navigation.controller;

import com.flatmap.navigation.ingestion.FacilityIngestionService;
import com.flatmap.navigation.ingestion.SourceResult;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 모든 외부 API 데이터를 한 번에 수집하는 통합 엔드포인트 (관리용: X-Admin-Api-Key 필요).
 */
@RestController
@RequestMapping("/api/data")
public class DataFetchController {

    private static final Logger log = LoggerFactory.getLogger(DataFetchController.class);

    private final GyeonggiDataService gyeonggiDataService;
    private final DisasterDataService disasterDataService;
    private final PublicDataService publicDataService;
    private final FacilityIngestionService facilityIngestionService;

    public DataFetchController(GyeonggiDataService gyeonggiDataService,
                                DisasterDataService disasterDataService,
                                PublicDataService publicDataService,
                                FacilityIngestionService facilityIngestionService) {
        this.facilityIngestionService = facilityIngestionService;
        this.gyeonggiDataService = gyeonggiDataService;
        this.disasterDataService = disasterDataService;
        this.publicDataService = publicDataService;
    }

    /**
     * 모든 외부 API를 한 번에 호출하여 DB에 저장한다.
     * 각 데이터별 저장 건수를 반환하고, 실패한 항목은 이름만 failedSources 로 알린다(원인은 서버 로그에만 남긴다).
     */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllData() {
        log.info("========== 전체 데이터 통합 수집 시작 ==========");
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        List<String> failedSources = new ArrayList<>();

        // === 급경사지 데이터 ===
        result.put("steepSlope_gyeonggi", safeFetch("경기데이터드림 오르막차로",
                gyeonggiDataService::fetchAndSaveUphillLanes, failedSources));
        result.put("steepSlope_disaster", safeFetch("재난안전데이터 급경사지",
                disasterDataService::fetchAndSaveSteepSlopes, failedSources));
        result.put("steepSlope_publicData", safeFetch("공공데이터포털 급경사지",
                publicDataService::fetchAndSaveSteepSlopes, failedSources));

        // === 안전시설 데이터 ===
        List<SourceResult> facilities = facilityIngestionService.syncAll();
        result.put("facilities", facilities);
        facilities.stream().filter(r -> !r.isSuccess()).forEach(r -> failedSources.add(r.source()));
        result.put("accidentZones", safeFetch("사고다발지",
                gyeonggiDataService::fetchAndSaveAccidentZones, failedSources));

        // === 공공데이터포털 추가 데이터 ===
        result.put("protectedZones", safeFetch("교통약자 보호구역",
                publicDataService::fetchAndSaveProtectedZones, failedSources));
        result.put("welfareFacilities", safeFetch("장애인복지시설",
                publicDataService::fetchAndSaveWelfareFacilities, failedSources));
        result.put("shadeShelters", safeFetch("그늘막",
                publicDataService::fetchAndSaveShadeShelters, failedSources));
        result.put("seniorCenters", safeFetch("노인복지관",
                publicDataService::fetchAndSaveSeniorCenters, failedSources));

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("elapsedMs", elapsed);
        if (!failedSources.isEmpty()) result.put("failedSources", failedSources);
        log.info("========== 전체 데이터 통합 수집 완료 ({}ms) ==========", elapsed);
        return ResponseEntity.ok(result);
    }

    private int safeFetch(String name, Callable<Integer> task, List<String> failedSources) {
        try {
            int count = task.call();
            log.info("[통합수집] {} → {}건 저장", name, count);
            return count;
        } catch (Exception e) {
            log.error("[통합수집] {} 실패: {}", name, e.getMessage(), e);
            failedSources.add(name);
            return 0;
        }
    }
}
