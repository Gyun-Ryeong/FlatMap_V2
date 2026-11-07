package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.entity.*;
import com.flatmap.navigation.model.SafetyFacility;
import com.flatmap.navigation.repository.*;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SafetyDataController(SafetyFacilityRepository safetyFacilityRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                ProtectedZoneRepository protectedZoneRepository,
                                WelfareFacilityRepository welfareFacilityRepository,
                                ShadeShelterRepository shadeShelterRepository,
                                SeniorCenterRepository seniorCenterRepository,
                                GyeonggiDataService gyeonggiDataService,
                                PublicDataService publicDataService) {
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

    @PostMapping("/fetch/security-lights")
    public ResponseEntity<Map<String, Object>> fetchSecurityLights() {
        log.info("보안등 실제 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveSecurityLights();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "보안등 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("보안등 데이터 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/accident-zones")
    public ResponseEntity<Map<String, Object>> fetchAccidentZones() {
        log.info("사고다발지 실제 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveAccidentZones();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "사고다발지 데이터 수집 완료"));
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
        } catch (Exception e) {
            log.error("노인복지관+경로당 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    @PostMapping("/fetch/cctv")
    public ResponseEntity<Map<String, Object>> fetchCctv() {
        log.info("CCTV 실제 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveCctvLocations();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "CCTV 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("CCTV 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    /** 전체 안전 데이터 수집. 실패한 항목은 이름만 failedSources 로 알린다(원인은 서버 로그에만). */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllSafetyData() {
        log.info("전체 안전 데이터 수집 시작");
        Map<String, Object> result = new java.util.HashMap<>();
        List<String> failedSources = new ArrayList<>();

        result.put("securityLights", safeFetch("보안등", () -> gyeonggiDataService.fetchAndSaveSecurityLights(), failedSources));
        result.put("accidentZones", safeFetch("사고다발지", () -> gyeonggiDataService.fetchAndSaveAccidentZones(), failedSources));
        result.put("protectedZones", safeFetch("교통약자보호구역", () -> publicDataService.fetchAndSaveProtectedZones(), failedSources));
        result.put("welfareFacilities", safeFetch("장애인복지시설", () -> publicDataService.fetchAndSaveWelfareFacilities(), failedSources));
        result.put("shadeShelters", safeFetch("그늘막", () -> publicDataService.fetchAndSaveShadeShelters(), failedSources));
        result.put("seniorCenters", safeFetch("노인복지관+경로당", () -> gyeonggiDataService.fetchAndSaveSeniorCenters(), failedSources));
        result.put("cctv", safeFetch("CCTV", () -> publicDataService.fetchAndSaveCctvLocations(), failedSources));

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
