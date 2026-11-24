package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.AccidentZone;
import com.flatmap.navigation.entity.SeniorCenter;
import com.flatmap.navigation.external.GyeonggiApiException;
import com.flatmap.navigation.external.GyeonggiHostGuard;
import com.flatmap.navigation.ingestion.IngestionHttp;
import com.flatmap.navigation.model.SteepSlopeArea;
import com.flatmap.navigation.repository.AccidentZoneRepository;
import com.flatmap.navigation.repository.SeniorCenterRepository;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class GyeonggiDataService {

    private static final Logger log = LoggerFactory.getLogger(GyeonggiDataService.class);
    private static final String GG_BASE = "https://openapi.gg.go.kr";
    /** 한 서비스에서 받을 최대 페이지 수 — 응답이 끝나지 않는 경우의 안전장치. */
    private static final int MAX_PAGES = 200;

    private final IngestionHttp http;
    private final GyeonggiHostGuard guard;
    private final long pageDelayMs;
    private long lastCallAtMs = 0;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;
    private final SteepSlopeAreaRepository steepSlopeAreaRepository;
    private final AccidentZoneRepository accidentZoneRepository;
    private final SeniorCenterRepository seniorCenterRepository;

    @Autowired
    public GyeonggiDataService(IngestionHttp http, GyeonggiHostGuard guard,
                                @Value("${gyeonggi.page-delay-ms:500}") long pageDelayMs,
                                ApiKeyConfig apiKeyConfig,
                                ObjectMapper objectMapper,
                                SteepSlopeAreaRepository steepSlopeAreaRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                SeniorCenterRepository seniorCenterRepository) {
        this.http = http;
        this.guard = guard;
        this.pageDelayMs = pageDelayMs;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
        this.steepSlopeAreaRepository = steepSlopeAreaRepository;
        this.accidentZoneRepository = accidentZoneRepository;
        this.seniorCenterRepository = seniorCenterRepository;
    }

    // ============================================================
    // 공통 호출 — 호출 간 지연, 순차 실행, 차단 감지
    // ============================================================

    /**
     * 경기데이터드림 1회 호출. 같은 호스트로 나가는 모든 호출이 여기를 지나므로
     * (synchronized) 관리자 수집이 겹쳐 실행돼도 동시에 두드리지 않고, 호출 사이에 pageDelayMs 이상 간격을 둔다.
     * 차단 대기 중이면 네트워크를 건드리지 않고, 차단 페이지를 받으면 대기에 들어간 뒤 예외를 던진다.
     * 실패는 null 이 아니라 예외다 — 호출자가 부분 결과를 성공처럼 저장하지 못하게 한다.
     */
    private synchronized String callGyeonggiApi(String serviceName, String apiKey, int page, int size) {
        guard.requireOpen();
        pace();

        String url = String.format("%s/%s?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                GG_BASE, serviceName, apiKey, page, size);
        log.info("[경기] API 호출: {}", url.replace(apiKey, "***"));
        try {
            String raw = guard.inspect(http.get(url));
            if (raw == null || raw.isBlank()) {
                throw new GyeonggiApiException("[" + serviceName + "] 빈 응답");
            }
            return raw;
        } catch (GyeonggiApiException e) {
            throw e;
        } catch (IOException e) {
            // IngestionHttp 의 메시지는 URL 을 담지 않는다("HTTP 500", 타임아웃 등).
            log.error("[경기][{}] API 호출 실패: {}", serviceName, e.getMessage());
            throw new GyeonggiApiException("[" + serviceName + "] API 호출 실패", e);
        }
    }

    private void pace() {
        if (pageDelayMs > 0) {
            long wait = lastCallAtMs + pageDelayMs - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GyeonggiApiException("수집이 중단되었습니다");
                }
            }
        }
        lastCallAtMs = System.currentTimeMillis();
    }

    /**
     * 한 서비스의 모든 페이지를 순서대로 읽어 row 마다 consumer 를 호출한다.
     * 끝은 "데이터 없음(INFO-200)·row 없음·누적 건수가 list_total_count 에 도달"로만 판단한다.
     * 그 밖의 결과 코드(키 오류·호출 한도 초과 등)·해석 실패·호출 실패는 예외로 전체를 중단한다.
     */
    private void forEachRow(String serviceName, String apiKey, int pageSize, Consumer<JsonNode> consumer) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            String raw = callGyeonggiApi(serviceName, apiKey, page, pageSize);
            JsonNode root;
            try {
                root = objectMapper.readTree(raw);
            } catch (Exception e) {
                throw new GyeonggiApiException("[" + serviceName + "] 응답을 해석하지 못했습니다", e);
            }

            JsonNode top = root.path("RESULT");
            if (!top.isMissingNode()) {
                String code = top.path("CODE").asText("");
                if ("INFO-200".equals(code)) return; // 해당 페이지에 데이터 없음 = 끝
                throw new GyeonggiApiException("[" + serviceName + "] API 결과 코드 " + code);
            }

            JsonNode rows = findRows(root);
            if (rows == null || rows.isEmpty()) return;
            if (page == 1) log.info("[{}] 첫 row: {}", serviceName, rows.get(0));
            for (JsonNode row : rows) consumer.accept(row);

            int total = totalCount(root);
            log.info("[{}] page={} {}건 조회 (전체 {}건)", serviceName, page, rows.size(), total);
            if (rows.size() < pageSize || (total > 0 && (long) page * pageSize >= total)) return;
        }
        throw new GyeonggiApiException("[" + serviceName + "] 페이지가 " + MAX_PAGES + "를 넘어 중단했습니다");
    }

    private int totalCount(JsonNode root) {
        var names = root.fieldNames();
        while (names.hasNext()) {
            JsonNode svc = root.path(names.next());
            if (svc.isArray() && !svc.isEmpty()) {
                int n = svc.get(0).path("head").path(0).path("list_total_count").asInt(0);
                if (n > 0) return n;
            }
        }
        return 0;
    }

    /** Extract the row array from Gyeonggi-style JSON: { "ServiceName": [{head}, {row:[...]}] } */
    private JsonNode findRows(JsonNode root) {
        var fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            JsonNode service = root.path(key);
            if (service.isArray() && service.size() > 1) {
                JsonNode rows = service.get(1).path("row");
                if (rows.isArray() && !rows.isEmpty()) return rows;
            }
        }
        return null;
    }

    // ============================================================
    // 오르막차로 (SteepSlopeArea)
    // ============================================================

    public int fetchAndSaveUphillLanes() {
        log.info("=== 경기데이터드림 오르막차로 데이터 수집 시작 ===");
        List<SteepSlopeArea> allAreas = new ArrayList<>();
        // 중간에 실패하면 예외가 올라가 저장하지 않는다 — 부분 결과로 기존 데이터를 덮어쓰지 않는다.
        forEachRow("UphillLane", apiKeyConfig.getGyeonggiUphillLaneApiKey(), 100, row -> {
            SteepSlopeArea area = parseUphillLaneRow(row);
            if (area != null) allAreas.add(area);
        });

        if (!allAreas.isEmpty()) {
            steepSlopeAreaRepository.replaceSource("GYEONGGI", allAreas);
            log.info("GYEONGGI 오르막차로 {}건 저장", allAreas.size());
        }
        return allAreas.size();
    }

    private SteepSlopeArea parseUphillLaneRow(JsonNode row) {
        String name = getFieldValue(row, "ROAD_NM", "SECT_NM", "UPHILL_LANE_NM", "NM", "MANAGE_NM");
        if (name == null) name = "오르막차로 구간";
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT", "Y");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LNG", "LOT", "X");
        if (lat == null || lng == null) return null;
        Double grade = getFieldDouble(row, "GRADIENT", "GRADE", "SLOPE", "INCLINE_RATE");
        if (grade == null) grade = 0.0;
        String regionCode = getFieldValue(row, "SIGUN_CD", "REGION_CD", "SIGNGU_CD");
        return new SteepSlopeArea(name, lat, lng, grade, "GYEONGGI", regionCode);
    }

    // 보안등(SECRTLGT) 수집은 ingestion 패키지(FacilityIngestionService)로 옮겼다 — 01 단계.

    // ============================================================
    // 사고다발지 — IceTfcacdar (결빙) + TfcacdarM (일반)
    // ============================================================

    public int fetchAndSaveAccidentZones() {
        log.info("=== 경기데이터드림 사고다발지 데이터 수집 시작 ===");
        List<AccidentZone> all = new ArrayList<>();

        all.addAll(fetchAccidentService("IceTfcacdar", "ICY"));
        all.addAll(fetchAccidentService("TfcacdarM", "NORMAL"));

        if (!all.isEmpty()) {
            accidentZoneRepository.deleteAll();
            accidentZoneRepository.saveAll(all);
            log.info("사고다발지 총 {}건 저장 완료 (성남시 필터링 후)", all.size());
        }
        return all.size();
    }

    private List<AccidentZone> fetchAccidentService(String serviceName, String type) {
        List<AccidentZone> result = new ArrayList<>();
        forEachRow(serviceName, apiKeyConfig.getGyeonggiApiKey(), 1000, row -> {
            AccidentZone z = parseAccidentRow(row, type, serviceName);
            if (z != null) result.add(z);
        });
        log.info("[{}] 성남시 {}건", serviceName, result.size());
        return result;
    }

    private AccidentZone parseAccidentRow(JsonNode row, String type, String serviceName) {
        String regionCode = getFieldValue(row, "SIGUN_CD", "SIGUN_NM");
        // 성남시 SIGUN_CD: 41130(성남시), 41131(수정구), 41133(중원구), 41135(분당구)
        if (!isSeongnam(regionCode)) return null;

        String name = getFieldValue(row, "LOC_INFO", "ACDNT_DIV_NM", "SPOT_NM", "ACCDNT_SPOT_NM",
                "ACCDNT_NM", "ROAD_NM", "SECT_NM", "NM", "MANAGE_NM");
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT", "Y_COORD", "GPS_Y",
                "CRDNT_Y", "POINT_Y", "WGS84_LAT", "Y");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LOGT", "LNG", "LOT", "X_COORD",
                "GPS_X", "CRDNT_X", "POINT_X", "WGS84_LOGT", "X");
        if (lat == null || lng == null) return null;
        if (name == null) name = "ICY".equals(type) ? "결빙사고 구간" : "사고다발 구간";
        return new AccidentZone(name, lat, lng, type, regionCode);
    }

    // ============================================================
    // 노인복지관 + 경로당 — OldpsnLsrWelfaclt + SenircentFaclt
    // ============================================================

    public int fetchAndSaveSeniorCenters() {
        log.info("=== 경기데이터드림 노인복지관(OldpsnLsrWelfaclt) + 경로당(SenircentFaclt) 수집 시작 ===");
        List<SeniorCenter> all = new ArrayList<>();

        all.addAll(fetchSeniorService("OldpsnLsrWelfaclt", "노인복지관"));
        all.addAll(fetchSeniorService("SenircentFaclt", "경로당"));

        if (!all.isEmpty()) {
            seniorCenterRepository.deleteAll();
            seniorCenterRepository.saveAll(all);
            log.info("노인복지관+경로당 총 {}건 저장 완료 (성남시 필터링 후)", all.size());
        }
        return all.size();
    }

    private List<SeniorCenter> fetchSeniorService(String serviceName, String defaultName) {
        List<SeniorCenter> result = new ArrayList<>();
        forEachRow(serviceName, apiKeyConfig.getGyeonggiApiKey(), 1000, row -> {
            SeniorCenter c = parseSeniorCenterRow(row, defaultName);
            if (c != null) result.add(c);
        });
        log.info("[{}] 성남시 {}건", serviceName, result.size());
        return result;
    }

    private SeniorCenter parseSeniorCenterRow(JsonNode row, String defaultName) {
        String regionCode = getFieldValue(row, "SIGUN_CD", "SIGUN_NM");
        if (!isSeongnam(regionCode)) return null;
        String name = getFieldValue(row, "FACLT_NM", "INSTIT_NM", "WLFRE_FACLT_NM", "INST_NM", "NM");
        if (name == null) name = defaultName;
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LOGT", "LNG", "LOT");
        if (lat == null || lng == null) return null;
        String addr = getFieldValue(row, "REFINE_ROADNM_ADDR", "REFINE_LOTNO_ADDR", "ADDR");
        String phone = getFieldValue(row, "TELNO", "TEL", "PHONE");
        return new SeniorCenter(name, lat, lng, addr, phone, regionCode);
    }

    // ============================================================
    // Legacy raw-response getters (kept for backward compat)
    // ============================================================

    public String getUphillLanes(int page, int size) {
        return callGyeonggiApi("UphillLane", apiKeyConfig.getGyeonggiUphillLaneApiKey(), page, size);
    }

    // ============================================================
    // Utilities
    // ============================================================

    private boolean isSeongnam(String regionCode) {
        if (regionCode == null) return false;
        // 성남시: 41130, 수정구: 41131, 중원구: 41133, 분당구: 41135
        return regionCode.startsWith("4113");
    }

    private String getFieldValue(JsonNode row, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = row.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                String val = node.asText().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private Double getFieldDouble(JsonNode row, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = row.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                String val = node.asText().trim();
                if (!val.isEmpty()) {
                    try { return Double.parseDouble(val); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}
