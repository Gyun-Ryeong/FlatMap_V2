package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.external.GyeonggiBlockedException;
import com.flatmap.navigation.model.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.service.CsvImportService;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.ElevationService;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import com.flatmap.navigation.service.SteepSlopeGradeImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 급경사지(붕괴위험 지정지) 조회 및 데이터 수집·갱신.
 *
 * <p>조회(GET)는 공개, 수집·갱신(POST)은 관리용이며 {@code X-Admin-Api-Key} 가 필요하다
 * (SecurityConfig 참고). 이 데이터에는 보행 위험등급을 부여하지 않는다.
 */
@RestController
@RequestMapping("/api/steep-slope")
public class SteepSlopeDataController {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeDataController.class);

    private final GyeonggiDataService gyeonggiDataService;
    private final DisasterDataService disasterDataService;
    private final PublicDataService publicDataService;
    private final CsvImportService csvImportService;
    private final ElevationService elevationService;
    private final SteepSlopeGradeImportService gradeImportService;
    private final SteepSlopeAreaRepository repository;

    public SteepSlopeDataController(GyeonggiDataService gyeonggiDataService,
                                     DisasterDataService disasterDataService,
                                     PublicDataService publicDataService,
                                     CsvImportService csvImportService,
                                     ElevationService elevationService,
                                     SteepSlopeGradeImportService gradeImportService,
                                     SteepSlopeAreaRepository repository) {
        this.gyeonggiDataService = gyeonggiDataService;
        this.disasterDataService = disasterDataService;
        this.publicDataService = publicDataService;
        this.csvImportService = csvImportService;
        this.elevationService = elevationService;
        this.gradeImportService = gradeImportService;
        this.repository = repository;
    }

    // ========== 관리용: 수집 ==========

    /** 경기데이터드림 오르막차로 데이터 수집 */
    @PostMapping("/fetch/gyeonggi")
    public ResponseEntity<Map<String, Object>> fetchGyeonggiData() {
        log.info("경기데이터드림 오르막차로 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveUphillLanes();
            return ResponseEntity.ok(Map.of(
                    "source", "GYEONGGI",
                    "savedCount", saved,
                    "message", "경기데이터드림 오르막차로 데이터 수집 완료"
            ));
        } catch (GyeonggiBlockedException e) {
            log.error("경기데이터드림 수집 중단(차단): {}", e.getMessage());
            return ApiErrors.upstreamBlocked("source", "GYEONGGI");
        } catch (Exception e) {
            log.error("경기데이터드림 데이터 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal("source", "GYEONGGI");
        }
    }

    /** 재난안전데이터 급경사지 데이터 수집 */
    @PostMapping("/fetch/disaster")
    public ResponseEntity<Map<String, Object>> fetchDisasterData() {
        log.info("재난안전데이터 급경사지 데이터 수집 시작");
        try {
            int saved = disasterDataService.fetchAndSaveSteepSlopes();
            return ResponseEntity.ok(Map.of(
                    "source", "DISASTER",
                    "savedCount", saved,
                    "message", "재난안전데이터 급경사지 데이터 수집 완료"
            ));
        } catch (Exception e) {
            log.error("재난안전데이터 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal("source", "DISASTER");
        }
    }

    /** 공공데이터포털 급경사지 데이터 수집 */
    @PostMapping("/fetch/public-data")
    public ResponseEntity<Map<String, Object>> fetchPublicData() {
        log.info("공공데이터포털 급경사지 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveSteepSlopes();
            return ResponseEntity.ok(Map.of(
                    "source", "PUBLIC_DATA",
                    "savedCount", saved,
                    "totalInDb", repository.count(),
                    "message", "공공데이터포털 급경사지 데이터 수집 완료"
            ));
        } catch (Exception e) {
            log.error("공공데이터포털 데이터 수집 실패: {}", e.getMessage(), e);
            return ApiErrors.internal("source", "PUBLIC_DATA");
        }
    }

    /** 모든 소스 수집 (경기데이터드림 + 재난안전데이터 + 공공데이터포털) */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllData() {
        log.info("전체 급경사지 데이터 수집 시작");
        int gyeonggiCount = 0;
        int disasterCount = 0;
        int publicDataCount = 0;
        List<String> failedSources = new ArrayList<>();

        try {
            gyeonggiCount = gyeonggiDataService.fetchAndSaveUphillLanes();
        } catch (Exception e) {
            log.error("경기데이터드림 수집 실패: {}", e.getMessage(), e);
            failedSources.add("GYEONGGI");
        }

        try {
            disasterCount = disasterDataService.fetchAndSaveSteepSlopes();
        } catch (Exception e) {
            log.error("재난안전데이터 수집 실패: {}", e.getMessage(), e);
            failedSources.add("DISASTER");
        }

        try {
            publicDataCount = publicDataService.fetchAndSaveSteepSlopes();
        } catch (Exception e) {
            log.error("공공데이터포털 수집 실패: {}", e.getMessage(), e);
            failedSources.add("PUBLIC_DATA");
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("gyeonggiSaved", gyeonggiCount);
        result.put("disasterSaved", disasterCount);
        result.put("publicDataSaved", publicDataCount);
        result.put("totalInDb", repository.count());
        if (!failedSources.isEmpty()) result.put("failedSources", failedSources);

        return ResponseEntity.ok(result);
    }

    // ========== 공개: 조회 ==========

    /** DB에 저장된 급경사지(붕괴위험 지정지) 조회. regionCode 접두사로 필터링할 수 있다. */
    @GetMapping
    public ResponseEntity<List<SteepSlopeArea>> getSteepSlopes(
            @RequestParam(required = false) String regionCode) {
        if (regionCode != null && !regionCode.isEmpty()) {
            return ResponseEntity.ok(repository.findByRegionCodeStartingWith(regionCode));
        }
        return ResponseEntity.ok(repository.findAll());
    }

    /** 출처별 조회 */
    @GetMapping("/source/{source}")
    public ResponseEntity<List<SteepSlopeArea>> getBySource(@PathVariable String source) {
        return ResponseEntity.ok(repository.findBySource(source.toUpperCase()));
    }

    /** 데이터 통계 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", repository.count());
        stats.put("gyeonggi", repository.countBySource("GYEONGGI"));
        stats.put("disaster", repository.countBySource("DISASTER"));
        stats.put("publicData", repository.countBySource("PUBLIC_DATA"));
        return ResponseEntity.ok(stats);
    }

    // ========== 관리용: 갱신 ==========

    /** Copernicus DEM(elevations.json) 기반 경사도(°) 갱신 */
    @PostMapping("/update-grade/elevation")
    public ResponseEntity<Map<String, Object>> updateGradeFromElevation() {
        log.info("고도 기반 경사도 업데이트 요청");
        try {
            Map<String, Object> result = elevationService.updateGradesFromElevation();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("고도 기반 업데이트 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    /**
     * 경사도(grade, 단위 각도 °)를 일괄 갱신한다.
     * 갱신 대상은 요청 본문의 id 목록으로 명시된다. grade가 null이면 값을 비운다.
     */
    @PostMapping("/update-grade/values")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateGradeValues(
            @RequestBody com.flatmap.navigation.dto.GradeUpdateRequest req) {
        log.info("경사도 값 일괄 갱신 요청 — {}건", req.getItems().size());

        List<String> problems = new ArrayList<>();
        int updated = 0;
        int cleared = 0;

        for (var it : req.getItems()) {
            if (!repository.updateGrade(it.getId(), it.getGrade())) {
                problems.add("대상 없음: id=" + it.getId());
                continue;
            }
            if (it.getGrade() == null) cleared++; else updated++;
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("updated", updated);
        result.put("cleared", cleared);
        result.put("gradeFilled", repository.countWithGrade());
        result.put("total", repository.count());
        result.put("moisCsvTotal", repository.countBySource("MOIS_CSV"));
        if (!problems.isEmpty()) result.put("problems", problems);

        log.info("경사도 갱신 완료 — {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * 성남시 급경사지와 경사도(°)를 번들 CSV(원본 FlatMap 값)로 적재한다. MOIS_CSV 출처를 통째로 교체하며
     * 몇 번 실행해도 결과가 같다. CSV 가 잘못되면 DB 는 바뀌지 않는다.
     */
    @PostMapping("/import/seongnam-grades")
    public ResponseEntity<Map<String, Object>> importSeongnamGrades() {
        log.info("성남시 급경사지 경사도 적재 요청");
        try {
            SteepSlopeGradeImportService.Result r = gradeImportService.importBundled();
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("parsed", r.parsed());
            response.put("saved", r.saved());
            response.put("withGrade", r.withGrade());
            response.put("totalInDb", repository.count());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("급경사지 경사도 적재 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    /** 행정안전부 CSV 성남시 급경사지 임포트 */
    @PostMapping("/import/csv")
    public ResponseEntity<Map<String, Object>> importFromCsv() {
        log.info("행정안전부 CSV 임포트 요청");
        try {
            Map<String, Integer> result = csvImportService.importSeongnamFromCsv();
            Map<String, Object> response = new java.util.HashMap<>(result);
            response.put("totalInDb", repository.count());
            response.put("message", "행정안전부 CSV 임포트 완료");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("CSV 임포트 실패: {}", e.getMessage(), e);
            return ApiErrors.internal();
        }
    }

    /**
     * 급경사지 동기화 (성남시_급경사지_현황.csv + 성남시 급경사지 현황.xlsx 병합 결과 적재).
     * MOIS_CSV 소스만 대상이며, 경사도(grade)는 일절 변경하지 않는다.
     */
    @PostMapping("/sync")
    @Transactional
    public ResponseEntity<Map<String, Object>> sync(@RequestBody com.flatmap.navigation.dto.SlopeSyncRequest req) {
        log.info("급경사지 동기화 시작 — delete={}, update={}, insert={}",
                req.getDelete().size(), req.getUpdate().size(), req.getInsert().size());

        List<String> problems = new ArrayList<>();

        // 1) 삭제 — MOIS_CSV 소스만 허용
        int deleted = 0;
        for (Long id : req.getDelete()) {
            var found = repository.findById(id);
            if (found.isEmpty()) { problems.add("삭제 대상 없음: id=" + id); continue; }
            if (!"MOIS_CSV".equals(found.get().getSource())) {
                problems.add("MOIS_CSV 아님 — 삭제 거부: id=" + id + " source=" + found.get().getSource());
                continue;
            }
            repository.deleteById(id);
            deleted++;
        }

        // 2) 갱신 — grade는 건드리지 않음
        int updated = 0;
        for (var it : req.getUpdate()) {
            var found = repository.findById(it.getId());
            if (found.isEmpty()) { problems.add("갱신 대상 없음: id=" + it.getId()); continue; }
            SteepSlopeArea e = found.get();
            if (!"MOIS_CSV".equals(e.getSource())) {
                problems.add("MOIS_CSV 아님 — 갱신 거부: id=" + it.getId() + " source=" + e.getSource());
                continue;
            }
            SteepSlopeArea next = new SteepSlopeArea();
            next.setName(it.getName());
            next.setSigungu(it.getSigungu());
            next.setEupmyeon(it.getEupmyeon());
            next.setJibun(it.getJibun());
            next.setRegionCode(it.getRegionCode());
            next.setLatitude(it.getLatitude());
            next.setLongitude(it.getLongitude());
            next.setIsMountain(it.getIsMountain());   // null 이면 기존 값 유지
            repository.updateDescriptive(it.getId(), next);
            updated++;
        }

        // 3) 신규 — grade는 null로 두고 사용자가 직접 채운다
        List<SteepSlopeArea> toInsert = new ArrayList<>();
        for (var it : req.getInsert()) {
            SteepSlopeArea e = new SteepSlopeArea();
            e.setName(it.getName());
            e.setSigungu(it.getSigungu());
            e.setEupmyeon(it.getEupmyeon());
            e.setJibun(it.getJibun());
            e.setRegionCode(it.getRegionCode());
            e.setLatitude(it.getLatitude());
            e.setLongitude(it.getLongitude());
            e.setIsMountain(Boolean.TRUE.equals(it.getIsMountain()));
            e.setSource("MOIS_CSV");
            toInsert.add(e);
        }
        repository.insertAll(toInsert);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("deleted", deleted);
        result.put("updated", updated);
        result.put("inserted", toInsert.size());
        result.put("moisCsvTotal", repository.countBySource("MOIS_CSV"));
        result.put("totalInDb", repository.count());
        if (!problems.isEmpty()) result.put("problems", problems);

        log.info("급경사지 동기화 완료 — {}", result);
        return ResponseEntity.ok(result);
    }
}
