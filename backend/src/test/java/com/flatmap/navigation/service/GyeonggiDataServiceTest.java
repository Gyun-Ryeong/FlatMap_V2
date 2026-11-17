package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.SeniorCenter;
import com.flatmap.navigation.external.GyeonggiApiException;
import com.flatmap.navigation.external.GyeonggiBlockedException;
import com.flatmap.navigation.external.GyeonggiHostGuard;
import com.flatmap.navigation.ingestion.IngestionHttp;
import com.flatmap.navigation.repository.AccidentZoneRepository;
import com.flatmap.navigation.repository.SeniorCenterRepository;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 외부 호출 없음 — IngestionHttp 를 가짜로 대체한다. 수집이 실패하면 DB 가 바뀌지 않는지를 확인한다. */
class GyeonggiDataServiceTest {

    private static final String KEY = "test-gg-key-do-not-leak";
    private static final String BLOCK_PAGE = "<html><head><meta charset=\"euc-kr\"></head><body>blocked</body></html>";

    private SeniorCenterRepository seniorRepo;
    private AccidentZoneRepository accidentRepo;
    private SteepSlopeAreaRepository steepRepo;
    private GyeonggiHostGuard guard;
    private ApiKeyConfig keys;
    private final List<String> requested = new ArrayList<>();

    @BeforeEach
    void setUp() {
        seniorRepo = mock(SeniorCenterRepository.class);
        accidentRepo = mock(AccidentZoneRepository.class);
        steepRepo = mock(SteepSlopeAreaRepository.class);
        guard = new GyeonggiHostGuard(60);
        keys = mock(ApiKeyConfig.class);
        when(keys.getGyeonggiApiKey()).thenReturn(KEY);
        when(keys.getGyeonggiUphillLaneApiKey()).thenReturn(KEY);
        requested.clear();
    }

    private GyeonggiDataService service(IngestionHttp http, long pageDelayMs) {
        return new GyeonggiDataService(http, guard, pageDelayMs, keys, new ObjectMapper(),
                steepRepo, accidentRepo, seniorRepo);
    }

    /** 요청 URL 에서 "서비스명:페이지" 키를 만들어 응답을 고른다. 모든 호출을 requested 에 기록한다. */
    private IngestionHttp http(Function<String, String> byServiceAndPage) {
        return url -> {
            requested.add(url);
            String service = url.substring(url.indexOf(".kr/") + 4, url.indexOf('?'));
            String page = url.replaceAll(".*pIndex=(\\d+).*", "$1");
            return byServiceAndPage.apply(service + ":" + page);
        };
    }

    private static String row(String sigunCd, String name) {
        return "{\"SIGUN_CD\":\"" + sigunCd + "\",\"FACLT_NM\":\"" + name + "\","
                + "\"REFINE_WGS84_LAT\":\"37.40\",\"REFINE_WGS84_LOGT\":\"127.12\"}";
    }

    private static String page(String service, int total, String... rows) {
        return "{\"" + service + "\":[{\"head\":[{\"list_total_count\":" + total + "},"
                + "{\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"ok\"}}]},{\"row\":[" + String.join(",", rows) + "]}]}";
    }

    private static String emptyPage() {
        return "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"no data\"}}";
    }

    @SuppressWarnings("unchecked")
    @Test
    void savesOnlySeongnamRowsAndStopsAtTotalWithoutAnExtraCall() {
        IngestionHttp http = http(k -> switch (k) {
            case "OldpsnLsrWelfaclt:1" -> page("OldpsnLsrWelfaclt", 2, row("41135", "분당복지관"), row("41820", "가평복지관"));
            case "SenircentFaclt:1" -> page("SenircentFaclt", 1, row("41131", "수정경로당"));
            default -> throw new AssertionError("예상 밖 호출 " + k);
        });

        int saved = service(http, 0).fetchAndSaveSeniorCenters();

        assertThat(saved).isEqualTo(2);
        assertThat(requested).hasSize(2); // 전체 건수에 도달하면 다음 페이지를 부르지 않는다
        ArgumentCaptor<List<SeniorCenter>> captor = ArgumentCaptor.forClass(List.class);
        verify(seniorRepo).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(SeniorCenter::getName).containsExactly("분당복지관", "수정경로당");
    }

    @Test
    void blockPageMidwayAbortsWithoutTouchingTheDatabaseAndStopsCallingTheHost() {
        // 경로당 1페이지(1000건, 총 10296)까지 정상 → 2페이지에서 차단 페이지
        String[] full = new String[1000];
        Arrays.fill(full, row("41135", "경로당"));
        IngestionHttp http = http(k -> switch (k) {
            case "OldpsnLsrWelfaclt:1" -> page("OldpsnLsrWelfaclt", 1, row("41135", "복지관"));
            case "SenircentFaclt:1" -> page("SenircentFaclt", 10296, full);
            case "SenircentFaclt:2" -> BLOCK_PAGE;
            default -> throw new AssertionError("예상 밖 호출 " + k);
        });
        GyeonggiDataService s = service(http, 0);

        assertThatThrownBy(s::fetchAndSaveSeniorCenters).isInstanceOf(GyeonggiBlockedException.class);

        // 부분 결과(복지관 1건 + 경로당 1000건)로 기존 데이터를 지우거나 덮어쓰지 않았다.
        verifyNoInteractions(seniorRepo);
        assertThat(requested).hasSize(3);

        // 차단이 감지됐으니 이후 호출은 네트워크로 나가지 않는다.
        assertThatThrownBy(s::fetchAndSaveSeniorCenters).isInstanceOf(GyeonggiBlockedException.class);
        assertThat(requested).hasSize(3);
    }

    @Test
    void blockedFromTheFirstCallLeavesDatabaseUntouched() {
        GyeonggiDataService s = service(http(k -> BLOCK_PAGE), 0);

        assertThatThrownBy(s::fetchAndSaveAccidentZones).isInstanceOf(GyeonggiBlockedException.class);

        verifyNoInteractions(accidentRepo);
        assertThat(requested).hasSize(1); // 결빙(IceTfcacdar)에서 막히면 일반(TfcacdarM)은 부르지도 않는다
    }

    @Test
    void secondServiceFailureDoesNotSaveTheFirstServicesRows() {
        IngestionHttp failing = url -> {
            if (url.contains("TfcacdarM")) throw new IOException("Read timed out");
            return page("IceTfcacdar", 1,
                    "{\"SIGUN_CD\":\"41135\",\"LOC_INFO\":\"결빙구간\",\"REFINE_WGS84_LAT\":\"37.40\",\"REFINE_WGS84_LOGT\":\"127.12\"}");
        };

        assertThatThrownBy(() -> service(failing, 0).fetchAndSaveAccidentZones())
                .isInstanceOf(GyeonggiApiException.class)
                .hasMessageNotContaining(KEY);

        verify(accidentRepo, never()).deleteAll();
        verify(accidentRepo, never()).saveAll(anyIterable());
    }

    @Test
    void resultCodeOtherThanNoDataIsAFailureNotAnEndOfData() {
        // 키 오류·호출 한도 초과 등은 "데이터 없음"이 아니다. 예전에는 조용히 끝으로 처리되어 0건 성공으로 보였다.
        GyeonggiDataService s = service(http(k -> "{\"RESULT\":{\"CODE\":\"ERROR-337\",\"MESSAGE\":\"limit\"}}"), 0);

        assertThatThrownBy(s::fetchAndSaveSeniorCenters)
                .isInstanceOf(GyeonggiApiException.class)
                .hasMessageContaining("ERROR-337")
                .isNotInstanceOf(GyeonggiBlockedException.class);
        verifyNoInteractions(seniorRepo);
    }

    @Test
    void noDataResultCodeIsTheNormalEnd() {
        GyeonggiDataService s = service(http(k -> emptyPage()), 0);

        assertThat(s.fetchAndSaveSeniorCenters()).isZero();
        verifyNoInteractions(seniorRepo); // 0건이면 기존 데이터를 지우지 않는다(기존 동작 유지)
    }

    @Test
    void unparsableBodyIsAFailure() {
        GyeonggiDataService s = service(http(k -> "not json at all"), 0);

        assertThatThrownBy(s::fetchAndSaveSeniorCenters).isInstanceOf(GyeonggiApiException.class);
        verifyNoInteractions(seniorRepo);
    }

    @Test
    void callsAreSpacedByTheConfiguredDelay() {
        IngestionHttp http = http(k -> switch (k) {
            case "OldpsnLsrWelfaclt:1" -> page("OldpsnLsrWelfaclt", 1, row("41135", "복지관"));
            case "SenircentFaclt:1" -> page("SenircentFaclt", 1, row("41135", "경로당"));
            default -> throw new AssertionError(k);
        });
        List<Long> times = new ArrayList<>();
        IngestionHttp timed = url -> {
            times.add(System.nanoTime());
            return http.get(url);
        };

        service(timed, 120).fetchAndSaveSeniorCenters();

        assertThat(times).hasSize(2);
        // 서로 다른 서비스 사이에도 간격이 지켜진다(여유 10ms).
        assertThat(Duration.ofNanos(times.get(1) - times.get(0)).toMillis()).isGreaterThanOrEqualTo(110);
    }

    @Test
    void failureMessagesNeverContainTheApiKeyOrUrl() {
        IngestionHttp failing = url -> {
            throw new IOException("HTTP 500");
        };
        assertThatThrownBy(() -> service(failing, 0).fetchAndSaveSeniorCenters())
                .isInstanceOf(GyeonggiApiException.class)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("openapi.gg.go.kr");
    }
}
