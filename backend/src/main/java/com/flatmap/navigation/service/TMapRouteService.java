package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T Map 보행자 경로 호출 — FlatMap 의 단일 경로 조회와 SafetyRoad 의 후보 경로 3종을 하나로 합친 서비스.
 *
 * <ul>
 *   <li>{@link #getWalkingRoute}: 옵션 하나(SAFE/SHORT/FLAT)로 경로 한 개. 실패하면 예외(기존 동작).</li>
 *   <li>{@link #getCandidateRoutes}: searchOption 0 / 4 / 10 후보 3종. <b>하나가 실패해도 나머지로 진행</b>한다.</li>
 * </ul>
 *
 * 외부 호출에는 연결·읽기 타임아웃이 있고(tmap.*-timeout-ms), 로그와 예외에는 URL·키·응답 본문을 남기지 않는다.
 */
@Service
public class TMapRouteService {

    private static final Logger log = LoggerFactory.getLogger(TMapRouteService.class);
    private static final String TMAP_PEDESTRIAN_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    /** 후보 경로 3종: T Map searchOption → 표시 이름 (SafetyRoad 와 같은 값). */
    private static final Map<Integer, String> CANDIDATE_OPTIONS = candidateOptions();

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;

    /** 후보 경로 한 개. option 은 T Map searchOption 코드, name 은 화면에 쓰는 이름. */
    public record CandidateRoute(int searchOption, String name, RouteResponse route) {
    }

    @Autowired
    public TMapRouteService(ApiKeyConfig apiKeyConfig, ObjectMapper objectMapper,
                            @Value("${tmap.connect-timeout-ms}") int connectTimeoutMs,
                            @Value("${tmap.read-timeout-ms}") int readTimeoutMs) {
        this(withTimeouts(connectTimeoutMs, readTimeoutMs), apiKeyConfig, objectMapper);
    }

    /** 테스트에서 RestTemplate 을 바꿔 끼우기 위한 생성자. */
    TMapRouteService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
    }

    private static RestTemplate withTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private static Map<Integer, String> candidateOptions() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0, "일반");
        m.put(4, "대로우선");
        m.put(10, "최단");
        return java.util.Collections.unmodifiableMap(m);
    }

    public RouteResponse getWalkingRoute(double originLng, double originLat,
                                          double destLng, double destLat,
                                          String option) {
        return getWalkingRoute(originLng, originLat, destLng, destLat, option, null);
    }

    public RouteResponse getWalkingRoute(double originLng, double originLat,
                                          double destLng, double destLat,
                                          String option, String passList) {
        // 옵션 매핑: SAFE→0(추천), SHORT→10(최단거리), FLAT→30(계단제외)
        int searchOption = 0;
        if ("SHORT".equals(option)) {
            searchOption = 10;
        } else if ("FLAT".equals(option)) {
            searchOption = 30;
        }

        if (passList != null && !passList.isEmpty()) {
            log.info("T Map 경유지(passList): {}", passList);
        }
        log.info("T Map 보행자 경로 요청: origin=({},{}), dest=({},{}), option={}, searchOption={}",
                originLat, originLng, destLat, destLng, option, searchOption);

        return requestRoute(originLng, originLat, destLng, destLat, searchOption, passList);
    }

    /**
     * 후보 경로 3종(searchOption 0 / 4 / 10)을 조회한다. 하나가 실패하면 그 후보만 빼고 계속한다.
     * 모두 실패하면 빈 목록이다(예외를 던지지 않는다).
     */
    public List<CandidateRoute> getCandidateRoutes(double startLat, double startLng,
                                                   double endLat, double endLng) {
        List<CandidateRoute> results = new ArrayList<>();
        for (Map.Entry<Integer, String> candidate : CANDIDATE_OPTIONS.entrySet()) {
            int searchOption = candidate.getKey();
            try {
                RouteResponse route = requestRoute(startLng, startLat, endLng, endLat, searchOption, null);
                results.add(new CandidateRoute(searchOption, candidate.getValue(), route));
            } catch (RuntimeException e) {
                // 예외 메시지에는 외부 응답 내용이 섞일 수 있어 종류만 남긴다.
                log.warn("T Map 후보 경로 실패 (searchOption={}): {}", searchOption, e.getClass().getSimpleName());
            }
        }
        log.info("T Map 후보 경로 {}/{}개 조회", results.size(), CANDIDATE_OPTIONS.size());
        return results;
    }

    private RouteResponse requestRoute(double originLng, double originLat, double destLng, double destLat,
                                       int searchOption, String passList) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", apiKeyConfig.getTmapAppKey());

        Map<String, Object> body = new HashMap<>();
        body.put("startX", String.valueOf(originLng));
        body.put("startY", String.valueOf(originLat));
        body.put("endX", String.valueOf(destLng));
        body.put("endY", String.valueOf(destLat));
        body.put("startName", "출발지");
        body.put("endName", "도착지");
        body.put("searchOption", String.valueOf(searchOption));
        if (passList != null && !passList.isEmpty()) {
            body.put("passList", passList);
        }

        try {
            org.springframework.http.ResponseEntity<String> responseEntity = restTemplate.exchange(
                    TMAP_PEDESTRIAN_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return parsePedestrianResponse(responseEntity.getBody());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("T Map HTTP 에러: {}", e.getStatusCode());
            throw new RuntimeException("경로 검색에 실패했습니다.", e);
        } catch (RestClientException e) {
            log.error("T Map 보행자 경로 API 호출 실패: {}", e.getClass().getSimpleName());
            throw new RuntimeException("경로 검색에 실패했습니다.", e);
        }
    }

    /**
     * T Map 응답의 문자열 값에 NUL 등 제어문자가 그대로 섞여 오는 경우가 있어
     * (JSON 규격 위반으로 파싱이 실패함) 파싱 전에 제거한다. 탭·개행·복귀는 보존한다.
     */
    private static String stripControlChars(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private RouteResponse parsePedestrianResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(stripControlChars(body));
            JsonNode features = root.path("features");

            if (!features.isArray() || features.isEmpty()) {
                log.warn("T Map 경로 응답에 features가 없음");
                throw new RuntimeException("경로를 찾을 수 없습니다.");
            }

            // 첫 번째 Feature의 properties에서 총 거리/시간 추출
            JsonNode firstProps = features.get(0).path("properties");
            int totalDistance = firstProps.path("totalDistance").asInt();
            int totalTime = firstProps.path("totalTime").asInt();

            // LineString geometry에서 좌표 추출 (Point는 건너뜀)
            // T Map GeoJSON: [경도, 위도] → RouteResponse.Coord(위도, 경도)
            List<RouteResponse.Coord> coords = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                String type = geometry.path("type").asText();

                if ("LineString".equals(type)) {
                    JsonNode coordinates = geometry.path("coordinates");
                    for (JsonNode coord : coordinates) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        coords.add(new RouteResponse.Coord(lat, lng));
                    }
                }
            }

            if (coords.isEmpty()) {
                log.warn("T Map 경로 응답에 LineString 좌표가 없음");
                throw new RuntimeException("경로를 찾을 수 없습니다.");
            }

            log.info("T Map 경로 파싱 완료: 거리={}m, 시간={}초, 좌표={}개", totalDistance, totalTime, coords.size());

            return new RouteResponse(coords, totalDistance, totalTime);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("T Map 경로 응답 파싱 실패: {}", e.getClass().getSimpleName());
            throw new RuntimeException("경로 응답 처리에 실패했습니다.", e);
        }
    }
}
