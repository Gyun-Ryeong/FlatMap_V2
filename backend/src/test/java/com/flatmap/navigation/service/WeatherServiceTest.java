package com.flatmap.navigation.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.WeatherInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 날씨 서비스: 격자 캐시 히트/미스, 실패 시 fallback(+재시도 간격), 외부 응답 이상 처리. RestTemplate 은 모킹한다. */
class WeatherServiceTest {

    /** 테스트용 가짜 키. 요청 URL 에는 들어가지만 로그·결과에는 나오면 안 된다. */
    private static final String KEY = "FAKE-SERVICE-KEY-123";
    private static final double LAT = 37.4201, LNG = 127.1262;               // 성남시청 부근
    private static final double OTHER_LAT = 37.3600, OTHER_LNG = 127.1100;   // 다른 격자

    private static final String RAIN_JSON = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[
               {"category":"T1H","fcstValue":"12.5"},{"category":"PTY","fcstValue":"1"},
               {"category":"SKY","fcstValue":"4"},{"category":"WSD","fcstValue":"3.2"},
               {"category":"REH","fcstValue":"80"},{"category":"RN1","fcstValue":"2"}]}}}}
            """;
    private static final String CLEAR_JSON = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[
               {"category":"T1H","fcstValue":"20"},{"category":"PTY","fcstValue":"0"},
               {"category":"SKY","fcstValue":"1"},{"category":"WSD","fcstValue":"1"}]}}}}
            """;

    private RestTemplate http;
    private MutableClock clock;
    private WeatherService service;

    /** 시간을 직접 움직일 수 있는 시계. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-10T03:00:00Z");

        void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private ApiKeyConfig keys() {
        ApiKeyConfig keys = mock(ApiKeyConfig.class);
        when(keys.getWeatherApiKey()).thenReturn(KEY);
        return keys;
    }

    private WeatherService newService(long backoffMs) {
        return new WeatherService(http, keys(), clock, 30 * 60_000L, backoffMs, 3);
    }

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        clock = new MutableClock();
        // 성공 캐시 30분, 실패 후 60초 동안 재시도 안 함, 최대 3격자
        service = newService(60_000L);
    }

    @Test
    void secondCallForTheSameGridIsACacheHitWithoutAnExternalCall() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(RAIN_JSON);

        WeatherInfo first = service.getWeather(LAT, LNG);
        WeatherInfo second = service.getWeather(LAT + 0.0005, LNG + 0.0005);   // 같은 5km 격자 안

        assertThat(first.getPrecipitationType()).isEqualTo(1);
        assertThat(first.isFallback()).isFalse();
        assertThat(second).isSameAs(first);
        verify(http, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void differentGridIsACacheMiss() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(RAIN_JSON);

        service.getWeather(LAT, LNG);
        service.getWeather(OTHER_LAT, OTHER_LNG);

        verify(http, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void cacheExpiresAfterTheTtl() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(RAIN_JSON, CLEAR_JSON);

        WeatherInfo first = service.getWeather(LAT, LNG);
        clock.advanceSeconds(30 * 60 - 1);
        assertThat(service.getWeather(LAT, LNG)).isSameAs(first);                 // 만료 1초 전: 히트
        verify(http, times(1)).getForObject(anyString(), eq(String.class));

        clock.advanceSeconds(1);                                                  // 정확히 TTL: 미스
        WeatherInfo refreshed = service.getWeather(LAT, LNG);
        assertThat(refreshed.getPrecipitationType()).isEqualTo(0);
        verify(http, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void apiFailureFallsBackWithoutThrowingAndMarksItAsFallback() {
        when(http.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error on GET request for \"http://x?serviceKey=" + KEY + "\""));

        WeatherInfo w = service.getWeather(LAT, LNG);

        assertThat(w.isFallback()).isTrue();
        assertThat(w.getDescription()).isEqualTo("날씨 정보 없음");
        assertThat(w.getWarning()).isNull();
    }

    @Test
    void failureIsNotRetriedWithinTheBackoffThenRetriedAfterIt() {
        when(http.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(RAIN_JSON);

        assertThat(service.getWeather(LAT, LNG).isFallback()).isTrue();
        clock.advanceSeconds(59);
        assertThat(service.getWeather(LAT, LNG).isFallback()).isTrue();           // 아직 재시도 간격 안
        verify(http, times(1)).getForObject(anyString(), eq(String.class));

        clock.advanceSeconds(1);
        WeatherInfo recovered = service.getWeather(LAT, LNG);                     // 간격이 지나 다시 호출 → 성공
        assertThat(recovered.isFallback()).isFalse();
        assertThat(recovered.getPrecipitationType()).isEqualTo(1);
        verify(http, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void nonZeroResultCodeEmptyItemsAndNonJsonBodyAreAllFallbacks() {
        String[] bad = {
                "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE KEY IS NOT REGISTERED\"}}}",
                "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":[]}}}}",
                "<OpenAPI_ServiceResponse><cmmMsgHeader/></OpenAPI_ServiceResponse>",
        };
        for (String body : bad) {
            RestTemplate fresh = mock(RestTemplate.class);
            when(fresh.getForObject(anyString(), eq(String.class))).thenReturn(body);
            WeatherService svc = new WeatherService(fresh, keys(), clock, 60_000L, 0L, 3);
            assertThat(svc.getWeather(LAT, LNG).isFallback()).as(body).isTrue();
        }
    }

    @Test
    void nullBodyIsAFallback() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(null);
        assertThat(service.getWeather(LAT, LNG).isFallback()).isTrue();
    }

    @Test
    void zeroBackoffRetriesOnEveryCallAfterAFailure() {
        WeatherService noBackoff = newService(0);
        when(http.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout")).thenReturn(CLEAR_JSON);

        assertThat(noBackoff.getWeather(LAT, LNG).isFallback()).isTrue();
        assertThat(noBackoff.getWeather(LAT, LNG).isFallback()).isFalse();
    }

    @Test
    void cacheSizeIsBounded() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(CLEAR_JSON);
        // 서로 다른 격자 5곳 → 최대 3 이므로 가득 차면 비운다. 예외 없이 계속 동작하고, 최근 격자는 캐시에 남는다.
        double[][] points = {{37.30, 127.00}, {37.40, 127.10}, {37.50, 127.20}, {37.60, 127.30}, {37.70, 127.40}};
        for (double[] p : points) service.getWeather(p[0], p[1]);
        verify(http, times(5)).getForObject(anyString(), eq(String.class));

        service.getWeather(37.70, 127.40);                 // 마지막 격자는 아직 캐시에 있다
        verify(http, times(5)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void failureLogsDoNotContainTheServiceKey() {
        when(http.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error on GET request for \"http://x?serviceKey=" + KEY + "\""));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WeatherService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.getWeather(LAT, LNG);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(KEY).doesNotContain("serviceKey");
            assertThat(event.getThrowableProxy()).isNull();     // 스택트레이스(메시지에 URL 포함)도 남기지 않는다
        }
    }

    @Test
    void requestUsesKstBaseTimeEvenWhenTheServerTimeZoneIsUtc() {
        // UTC 03:00 = KST 12:00 → 분 < 45 이므로 직전 정시의 30분 발표(11:30)
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(CLEAR_JSON);
        Clock kst = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        new WeatherService(http, keys(), kst, 60_000L, 0L, 3).getWeather(LAT, LNG);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(http).getForObject(url.capture(), eq(String.class));
        assertThat(url.getValue()).contains("base_date=20261110").contains("base_time=1130");
    }
}
