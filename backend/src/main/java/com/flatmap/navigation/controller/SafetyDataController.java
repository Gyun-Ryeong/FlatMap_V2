package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.external.GyeonggiBlockedException;
import com.flatmap.navigation.entity.*;
import com.flatmap.navigation.ingestion.FacilityIngestionService;
import com.flatmap.navigation.ingestion.FacilitySource;
import com.flatmap.navigation.ingestion.SourceResult;
import com.flatmap.navigation.model.SafetyFacility;
import com.flatmap.navigation.repository.*;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 안전시설 조회(GET, 공개)와 데이터 수집(POST /fetch/**, 관리용 — X-Admin-Api-Key 필요).
 * CCTV·보안등은 safety_facility 통합 테이블에서 읽는다.
 */
@RestController
@RequestMapping("/api/safety")
public class SafetyDataController {

    private static final Logger log = LoggerFactory.getLogger(SafetyDataController.class);

    private final SafetyFacilityRepository safetyFacilityRepository;
    private final AccidentZoneRepository accidentZoneRepository;
    private final ProtectedZoneRepository protectedZoneRepository;
    private final WelfareFacilityRepository welfareFacilityRepository;
    private final ShadeShelterRepository shadeShelterRepository;
    private final SeniorCenterRepository seniorCenterRepository;
    private final GyeonggiDataService gyeonggiDataService;
    private final PublicDataService publicDataService;
    private final FacilityIngestionService facilityIngestionService;

    public SafetyDataController(SafetyFacilityRepository safetyFacilityRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                ProtectedZoneRepository protectedZoneRepository,
                                WelfareFacilityRepository welfareFacilityRepository,
                                ShadeShelterRepository shadeShelterRepository,
                                SeniorCenterRepository seniorCenterRepository,
                                GyeonggiDataService gyeonggiDataService,
                                PublicDataService publicDataService,
                                FacilityIngestionService facilityIngestionService) {
        this.facilityIngestionService = facilityIngestionService;
        this.safetyFacilityRepository = safetyFacilityRepository;
        this.accidentZoneRepository = accidentZoneRepository;
        this.protectedZoneRepository = protectedZoneRepository;
        this.welfareFacilityRepository = welfareFacilityRepository;
        this.shadeShelterRepository = shadeShelterRepository;
        this.seniorCenterRepository = seniorCenterRepository;
        this.gyeonggiDataService = gyeonggiDataService;
        this.publicDataService = publicDataService;
    }

    // ========== 보안등 ==========

    @GetMapping("/security-lights")
    public ResponseEntity<List<SafetyFacility>> getSecurityLights(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(safetyFacilityRepository
                    .findByTypeAndRegionCodePrefix(SafetyFacility.TYPE_SECURITY_LIGHT, regionCode));
        }
        return ResponseEntity.ok(safetyFacilityRepository.findByType(SafetyFacility.TYPE_SECURITY_LIGHT));
    }

    // ========== 지도 영역 시설 조회 (CCTV·보안등·경찰시설) ==========

    static final int DEFAULT_FACILITY_LIMIT = 500;
    static final int MAX_FACILITY_LIMIT = 2000;
    private static final List<String> FACILITY_TYPES = List.of(
            SafetyFacility.TYPE_CCTV, SafetyFacility.TYPE_SECURITY_LIGHT, SafetyFacility.TYPE_POLICE);

    /**
     * 지도 뷰포트 안의 시설. type 은 쉼표로 여러 개(CCTV,SECURITY_LIGHT,POLICE), 생략하면 세 종류 모두.
     * limit 개를 넘으면 앞에서 limit 개만 주고 truncated=true 로 알린다(전체 보안등은 수십만 건이다).
     */
    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> getFacilitiesInBounds(
            @RequestParam double minLat,
            @RequestParam double minLng,
            @RequestParam double maxLat,
            @RequestParam double maxLng,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "" + DEFAULT_FACILITY_LIMIT) int limit) {

        if (!(minLat < maxLat) || !(minLng < maxLng)) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_BOUNDS", "조회 범위가 올바르지 않습니다.");
        }
        if (limit < 1 || limit > MAX_FACILITY_LIMIT) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_LIMIT",
                    "limit 은 1 이상 " + MAX_FACILITY_LIMIT + " 이하여야 합니다.");
        }
        List<String> types = new ArrayList<>();
        if (type == null || type.isBlank()) {
            types.addAll(FACILITY_TYPES);
        } else {
            for (String t : type.split(",")) {
                String name = t.trim().toUpperCase();
                if (!FACILITY_TYPES.contains(name)) {
                    return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_FACILITY_TYPE", "지원하지 않는 시설 종류입니다.");
                }
                if (!types.contains(name)) types.add(name);
            }
        }

        List<SafetyFacility> found = safetyFacilityRepository.findInBounds(types, minLat, minLng, maxLat, maxLng, limit + 1);
        boolean truncated = found.size() > limit;
        List<Map<String, Object>> items = new ArrayList<>();
        for (SafetyFacility f : truncated ? found.subList(0, limit) : found) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("facilityType", f.getFacilityType());
            item.put("name", f.getName());
            item.put("latitude", f.getLatitude());
            item.put("longitude", f.getLongitude());
            items.add(item);
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("count", items.size());
        body.put("limit", limit);
        body.put("truncated", truncated);
        body.put("facilities", items);
        return ResponseEntity.ok(body);
    }

    // ========== 사고다발지 ==========

    @GetMapping("/accident-zones")
    public ResponseEntity<List<AccidentZone>> getAccidentZones(
            @RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return ResponseEntity.ok(accidentZoneRepository.findByType(type.toUpperCase()));
        }
        return ResponseEntity.ok(accidentZoneRepository.findAll());
    }

    // ========== CCTV ==========

    @GetMapping("/cctv")
    public ResponseEntity<List<SafetyFacility>> getCctvLocations() {
        return ResponseEntity.ok(safetyFacilityRepository.findByType(SafetyFacility.TYPE_CCTV));
    }

    // ========== 교통약자 보호구역 ==========

    @GetMapping("/protected-zones")
    public ResponseEntity<List<ProtectedZone>> getProtectedZones(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(protectedZoneRepository.findByRegionCodeStartingWith(regionCode));
        }
        return ResponseEntity.ok(protectedZoneRepository.findAll());
    }

    // ========== 장애인복지시설 ==========

    @GetMapping("/welfare-facilities")
    public ResponseEntity<List<WelfareFacility>> getWelfareFacilities(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(welfareFacilityRepository.findByRegionCodeStartingWith(regionCode));
        }
        return ResponseEntity.ok(welfareFacilityRepository.findAll());
    }

    // ========== 그늘막 ==========

    @GetMapping("/shade-shelters")
    public ResponseEntity<List<ShadeShelter>> getShadeShelters(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(shadeShelterRepository.findByRegionCodeStartingWith(regionCode));
        }
        return ResponseEntity.ok(shadeShelterRepository.findAll());
    }

    // ========== 노인복지관 ==========

    @GetMapping("/senior-centers")
    public ResponseEntity<List<SeniorCenter>> getSeniorCenters(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(seniorCenterRepository.findByRegionCodeStartingWith(regionCode));
        }
        return ResponseEntity.ok(seniorCenterRepository.findAll());
    }

    // ========== 통계 ==========

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("securityLights", safetyFacilityRepository.countByType(SafetyFacility.TYPE_SECURITY_LIGHT));
        stats.put("accidentZones", accidentZoneRepository.count());
        stats.put("cctv", safetyFacilityRepository.countByType(SafetyFacility.TYPE_CCTV));
        stats.put("protectedZones", protectedZoneRepository.count());
        stats.put("welfareFacilities", welfareFacilityRepository.count());
        stats.put("shadeShelters", shadeShelterRepository.count());
        stats.put("seniorCenters", seniorCenterRepository.count());
        return ResponseEntity.ok(stats);
    }

    // ========== 관리용: 실제 API 데이터 수집 ==========

    /**
     * CCTV·보안등·경찰시설(safety_facility) 수집. 소스별 성공/실패와 건수만 응답한다(실패 원인은 서버 로그).
     * source 를 주면 그 소스만, 없으면 전체. 같은 호출을 반복해도 결과 행 수는 늘지 않는다(멱등).
     */
    @PostMapping("/facilities/sync")
    public ResponseEntity<Map<String, Object>> syncFacilities(@RequestParam(required = false) String source) {
        if (source == null || source.isBlank()) {
            return ResponseEntity.ok(Map.of("results", facilityIngestionService.syncAll()));
        }
        return FacilitySource.fromCode(source)
                .<ResponseEntity<Map<String, Object>>>map(s ->
                        ResponseEntity.ok(Map.of("results", List.of(facilityIngestionService.sync(s)))))
                .orElseGet(() -> ApiErrors.body(HttpStatus.BAD_REQUEST, "UNKNOWN_SOURCE", "알 수 없는 수집 소스입니다."));
    }

    @PostMapping("/fetch/accident-zones")
    public ResponseEntity<Map<String, Object>> fetchAccidentZones() {
        log.info("사고다발지 실제 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveAccidentZones();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "사고다발지 데이터 수집 완료"));
        } catch (GyeonggiBlockedException e) {
            log.error("사고다발지 수집 중단(차단): {}", e.getMessage());
            return ApiErrors.upstreamBlocked();
        } catch (Exception e) {
            log.error("사고다발지 데이터 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/protected-zones")
    public ResponseEntity<Map<String, Object>> fetchProtectedZones() {
        log.info("교통약자 보호구역 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveProtectedZones();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "교통약자 보호구역 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("교통약자 보호구역 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/welfare-facilities")
    public ResponseEntity<Map<String, Object>> fetchWelfareFacilities() {
        log.info("장애인복지시설 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveWelfareFacilities();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "장애인복지시설 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("장애인복지시설 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/shade-shelters")
    public ResponseEntity<Map<String, Object>> fetchShadeShelters() {
        log.info("그늘막 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveShadeShelters();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "그늘막 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("그늘막 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/senior-centers")
    public ResponseEntity<Map<String, Object>> fetchSeniorCenters() {
        log.info("노인복지관+경로당 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveSeniorCenters();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "노인복지관+경로당 데이터 수집 완료"));
        } catch (GyeonggiBlockedException e) {
            log.error("노인복지관+경로당 수집 중단(차단): {}", e.getMessage());
            return ApiErrors.upstreamBlocked();
        } catch (Exception e) {
            log.error("노인복지관+경로당 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    /** 전체 안전 데이터 수집. 실패한 항목은 이름만 failedSources 로 알린다(원인은 서버 로그에만). */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllSafetyData() {
        log.info("전체 안전 데이터 수집 시작");
        Map<String, Object> result = new java.util.HashMap<>();
        List<String> failedSources = new ArrayList<>();

        List<SourceResult> facilities = facilityIngestionService.syncAll();
        result.put("facilities", facilities);
        facilities.stream().filter(r -> !r.isSuccess()).forEach(r -> failedSources.add(r.source()));
        result.put("accidentZones", safeFetch("사고다발지", () -> gyeonggiDataService.fetchAndSaveAccidentZones(), failedSources));
        result.put("protectedZones", safeFetch("교통약자보호구역", () -> publicDataService.fetchAndSaveProtectedZones(), failedSources));
        result.put("welfareFacilities", safeFetch("장애인복지시설", () -> publicDataService.fetchAndSaveWelfareFacilities(), failedSources));
        result.put("shadeShelters", safeFetch("그늘막", () -> publicDataService.fetchAndSaveShadeShelters(), failedSources));
        result.put("seniorCenters", safeFetch("노인복지관+경로당", () -> gyeonggiDataService.fetchAndSaveSeniorCenters(), failedSources));

        if (!failedSources.isEmpty()) result.put("failedSources", failedSources);
        return ResponseEntity.ok(result);
    }

    private int safeFetch(String name, java.util.concurrent.Callable<Integer> task, List<String> failedSources) {
        try {
            return task.call();
        } catch (Exception e) {
            log.error("{} 수집 실패: {}", name, e.getMessage(), e);
            failedSources.add(name);
            return 0;
        }
    }
}
