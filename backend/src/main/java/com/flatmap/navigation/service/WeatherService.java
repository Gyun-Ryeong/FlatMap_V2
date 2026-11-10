package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.util.GridConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기상청 초단기예보 조회.
 *
 * <ul>
 *   <li>기상청 격자(nx,ny)별 메모리 캐시 — 성공은 weather.cache-ttl-minutes 동안 재사용(히트면 외부 호출 없음).</li>
 *   <li>실패하면 {@link WeatherInfo#fallback()}(fallback=true)을 돌려주고 weather.failure-backoff-seconds 동안
 *       같은 격자를 다시 부르지 않는다(후보 경로 3개가 연달아 타임아웃을 기다리지 않도록).
 *       대체값은 점수 보정에 쓰이지 않는다.</li>
 *   <li>외부 호출에는 연결·읽기 타임아웃이 있고, 로그에는 URL·서비스키·응답 본문을 남기지 않는다(예외 종류만).</li>
 * </ul>
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String API_URL =
            "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst";
    /** 기상청 발표 시각은 한국 시간 기준이라 서버 시간대와 무관하게 KST 로 계산한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final Clock clock;
    private final long cacheTtlMs;
    private final long failureBackoffMs;
    private final int maxCacheEntries;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 캐시: "nx,ny" → {weatherInfo, 만료 시각(ms)}. 성공과 실패(대체값)를 함께 담는다.
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public WeatherService(ApiKeyConfig apiKeyConfig,
                          @Value("${weather.connect-timeout-ms}") int connectTimeoutMs,
                          @Value("${weather.read-timeout-ms}") int readTimeoutMs,
                          @Value("${weather.cache-ttl-minutes}") long cacheTtlMinutes,
                          @Value("${weather.failure-backoff-seconds}") long failureBackoffSeconds,
                          @Value("${weather.cache-max-entries}") int maxCacheEntries) {
        this(withTimeouts(connectTimeoutMs, readTimeoutMs), apiKeyConfig, Clock.system(KST),
                cacheTtlMinutes * 60_000L, failureBackoffSeconds * 1_000L, maxCacheEntries);
    }

    /** 테스트에서 RestTemplate·시계를 바꿔 끼우기 위한 생성자. */
    WeatherService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig, Clock clock,
                   long cacheTtlMs, long failureBackoffMs, int maxCacheEntries) {
        if (cacheTtlMs <= 0 || failureBackoffMs < 0 || maxCacheEntries <= 0) {
            throw new IllegalArgumentException("날씨 캐시 설정이 올바르지 않다.");
        }
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.clock = clock;
        this.cacheTtlMs = cacheTtlMs;
        this.failureBackoffMs = failureBackoffMs;
        this.maxCacheEntries = maxCacheEntries;
    }

    private static RestTemplate withTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * 위경도 기반 현재 날씨 조회 (격자 캐시, 실패 시 fallback).
     */
    public WeatherInfo getWeather(double lat, double lng) {
        int[] grid = GridConverter.toGrid(lat, lng);
        int nx = grid[0];
        int ny = grid[1];
        String cacheKey = nx + "," + ny;
        long now = clock.millis();

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && now < entry.expiresAt) {
            log.debug("날씨 캐시 히트: grid({},{})", nx, ny);
            return entry.weather;
        }

        try {
            WeatherInfo weather = fetchFromApi(nx, ny);
            put(cacheKey, new CacheEntry(weather, now + cacheTtlMs), now);
            log.info("날씨 조회 성공: grid({},{}) → {}℃, PTY={}, SKY={}",
                    nx, ny, weather.getTemperature(), weather.getPrecipitationType(), weather.getSky());
            return weather;
        } catch (Exception e) {
            // 예외 메시지에는 요청 URL(서비스키 포함)이 섞일 수 있어 종류만 남긴다.
            log.warn("날씨 API 호출 실패 (fallback 사용): grid({},{}) {}", nx, ny, e.getClass().getSimpleName());
            WeatherInfo fallback = WeatherInfo.fallback();
            if (failureBackoffMs > 0) {
                put(cacheKey, new CacheEntry(fallback, now + failureBackoffMs), now);
            }
            return fallback;
        }
    }

    /** 캐시 크기를 제한한다(/api/weather 는 임의 좌표를 받는다). 가득 차면 만료분을 먼저 치우고, 그래도 가득이면 비운다. */
    private void put(String key, CacheEntry entry, long now) {
        if (cache.size() >= maxCacheEntries && !cache.containsKey(key)) {
            cache.values().removeIf(e -> now >= e.expiresAt);
            if (cache.size() >= maxCacheEntries) cache.clear();
        }
        cache.put(key, entry);
    }

    private WeatherInfo fetchFromApi(int nx, int ny) throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = resolveBaseTime(now);

        // 발표 시간이 전 시간대이고 자정 넘어간 경우
        if (baseTime.equals("2330") && now.getHour() == 0 && now.getMinute() < 45) {
            baseDate = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("serviceKey", apiKeyConfig.getWeatherApiKey())
                .queryParam("numOfRows", 60)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(false)
                .toUriString();

        log.info("[날씨] 기상청 API 호출: base_date={}, base_time={}, nx={}, ny={}", baseDate, baseTime, nx, ny);

        String response = restTemplate.getForObject(url, String.class);
        if (response == null) {
            throw new IllegalStateException("기상청 API 응답 없음");
        }
        return parseResponse(response);
    }

    private WeatherInfo parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode header = root.path("response").path("header");

        String resultCode = header.path("resultCode").asText();
        if (!"00".equals(resultCode)) {
            // 결과 코드만 담는다(resultMsg 는 외부 문구).
            throw new IllegalStateException("기상청 API 오류 코드 " + resultCode);
        }

        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalStateException("기상청 API 응답 데이터 없음");
        }

        WeatherInfo weather = new WeatherInfo();
        weather.setTemperature(20);
        weather.setPrecipitationType(0);
        weather.setRainfall("0");
        weather.setSky(1);
        weather.setHumidity(50);
        weather.setWindSpeed(1.0);

        for (JsonNode item : items) {
            String category = item.path("category").asText();
            String value = item.path("fcstValue").asText();

            switch (category) {
                case "T1H": weather.setTemperature(parseDouble(value, 20)); break;
                case "PTY": weather.setPrecipitationType(parseInt(value, 0)); break;
                case "RN1": weather.setRainfall(value); break;
                case "SKY": weather.setSky(parseInt(value, 1)); break;
                case "REH": weather.setHumidity(parseInt(value, 50)); break;
                case "WSD": weather.setWindSpeed(parseDouble(value, 1.0)); break;
            }
        }

        applyDescriptionAndIcon(weather);
        applyWarning(weather);
        return weather;
    }

    private void applyDescriptionAndIcon(WeatherInfo w) {
        int pty = w.getPrecipitationType();
        if (pty == 1 || pty == 5) {
            w.setDescription("비"); w.setIcon("rain");
        } else if (pty == 3 || pty == 7) {
            w.setDescription("눈"); w.setIcon("snow");
        } else if (pty == 2 || pty == 6) {
            w.setDescription("비/눈"); w.setIcon("sleet");
        } else {
            switch (w.getSky()) {
                case 1: w.setDescription("맑음"); w.setIcon("clear"); break;
                case 3: w.setDescription("구름많음"); w.setIcon("cloudy"); break;
                case 4: w.setDescription("흐림"); w.setIcon("overcast"); break;
                default: w.setDescription("맑음"); w.setIcon("clear");
            }
        }
        w.setDescription(w.getDescription() + ", " + String.format("%.0f", w.getTemperature()) + "℃");
    }

    private void applyWarning(WeatherInfo w) {
        int pty = w.getPrecipitationType();
        if (pty == 3 || pty == 7) {
            w.setWarning("눈 예보, 경사구간 결빙 주의");
        } else if (pty == 2 || pty == 6) {
            w.setWarning("비/눈 예보, 경사구간 미끄럼 주의");
        } else if (pty == 1 || pty == 5) {
            w.setWarning("비 예보, 경사구간 미끄럼 주의");
        } else if (w.getTemperature() <= 0) {
            w.setWarning("영하 기온, 경사구간 결빙 위험");
        } else if (w.getWindSpeed() >= 10) {
            w.setWarning("강풍 주의, 노출 구간 보행 주의");
        } else {
            w.setWarning(null);
        }
    }

    /**
     * 현재 시간 기준 가장 최근 초단기예보 발표 시간.
     * 초단기예보는 매시 30분에 발표, ~15분 뒤 조회 가능.
     */
    String resolveBaseTime(LocalDateTime now) {
        int hour = now.getHour();
        int minute = now.getMinute();

        if (minute >= 45) {
            return String.format("%02d30", hour);
        } else {
            int prevHour = (hour == 0) ? 23 : hour - 1;
            return String.format("%02d30", prevHour);
        }
    }

    private double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); }
        catch (Exception e) { return fallback; }
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception e) { return fallback; }
    }

    private static class CacheEntry {
        final WeatherInfo weather;
        final long expiresAt;
        CacheEntry(WeatherInfo weather, long expiresAt) {
            this.weather = weather;
            this.expiresAt = expiresAt;
        }
    }
}
