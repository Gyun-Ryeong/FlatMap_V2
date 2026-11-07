package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.model.SafetyFacility;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 호출 없음 — IngestionHttp 를 픽스처로 대체한다. */
class GyeonggiJsonReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String row(String addr, String lat, String lng) {
        return "{\"INSTL_PUPRS_DIV_NM\":\"방범용\",\"REFINE_ROADNM_ADDR\":\"" + addr + "\","
                + "\"REFINE_WGS84_LAT\":\"" + lat + "\",\"REFINE_WGS84_LOGT\":\"" + lng + "\"}";
    }

    private static String page(String service, int total, String... rows) {
        return "{\"" + service + "\":[{\"head\":[{\"list_total_count\":" + total + "},"
                + "{\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"ok\"}}]},{\"row\":[" + String.join(",", rows) + "]}]}";
    }

    private static IngestionSettings settings(int pageSize) {
        return new IngestionSettings(0, pageSize, 10, "", "gg-test-key", "od-test-key");
    }

    @Test
    void keepsOnlySeongnamRowsAndCountsExclusions() throws Exception {
        String body = page("CCTV", 5,
                row("경기도 성남시 분당구 정자동 1", "37.3600", "127.1080"),     // 통과
                row("경기도 가평군 상면 행현리 1", "37.7484", "127.3536"),       // 좌표 범위 밖
                row("경기도 용인시 수지구 1", "37.4000", "127.1000"),            // 범위 안이지만 주소가 성남이 아님
                row("", "37.4000", "127.1000"),                                 // 주소 없음 → 성남 확인 불가
                row("경기도 성남시 수정구 1", "0", "0"));                         // 좌표 무효
        GyeonggiJsonReader reader = GyeonggiJsonReader.cctv(url -> body, settings(1000), MAPPER);

        SourceReading r = reader.read();

        assertThat(r.read()).isEqualTo(5);
        assertThat(r.facilities()).hasSize(1);
        SafetyFacility f = r.facilities().get(0);
        assertThat(f.getFacilityType()).isEqualTo("CCTV");
        assertThat(f.getDataSource()).isEqualTo("GG_CCTV");
        assertThat(f.getName()).isEqualTo("방범용");
        assertThat(f.getAddress()).contains("성남시");
        assertThat(r.excluded(SourceReading.OUTSIDE_BOUNDS)).isEqualTo(1);
        assertThat(r.excluded(SourceReading.ADDRESS_NOT_SEONGNAM)).isEqualTo(2);
        assertThat(r.excluded(SourceReading.INVALID_COORDS)).isEqualTo(1);
        assertThat(r.excluded()).isEqualTo(4);
    }

    @Test
    void followsPagesUntilTotalIsReached() throws Exception {
        List<String> requested = new ArrayList<>();
        IngestionHttp http = url -> {
            requested.add(url);
            if (url.contains("pIndex=1&")) {
                return page("CCTV", 3, row("경기도 성남시 A", "37.40", "127.10"), row("경기도 성남시 B", "37.41", "127.11"));
            }
            return page("CCTV", 3, row("경기도 성남시 C", "37.42", "127.12"));
        };

        SourceReading r = GyeonggiJsonReader.cctv(http, settings(2), MAPPER).read();

        assertThat(r.facilities()).hasSize(3);
        assertThat(requested).hasSize(2);
        assertThat(requested.get(0)).contains("Type=json").contains("pSize=2");
    }

    @Test
    void noDataResultCodeEndsWithoutError() throws Exception {
        String noData = "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"해당하는 데이터가 없습니다.\"}}";
        SourceReading r = GyeonggiJsonReader.cctv(url -> noData, settings(1000), MAPPER).read();
        assertThat(r.read()).isZero();
        assertThat(r.facilities()).isEmpty();
    }

    @Test
    void errorResultCodeFailsWithoutLeakingMessageOrKey() {
        String invalidKey = "{\"RESULT\":{\"CODE\":\"INFO-300\",\"MESSAGE\":\"인증키가 유효하지 않습니다 gg-test-key\"}}";
        GyeonggiJsonReader reader = GyeonggiJsonReader.cctv(url -> invalidKey, settings(1000), MAPPER);

        assertThatThrownBy(reader::read)
                .isInstanceOf(IngestionException.class)
                .hasMessage("API result code INFO-300");
    }

    @Test
    void missingKeyFailsBeforeAnyHttpCall() {
        IngestionHttp http = url -> { throw new AssertionError("must not call"); };
        IngestionSettings noKey = new IngestionSettings(0, 1000, 10, "", " ", "");

        assertThatThrownBy(() -> GyeonggiJsonReader.cctv(http, noKey, MAPPER).read())
                .isInstanceOf(IngestionException.class);
    }

    @Test
    void securityLightUsesItsOwnCodeAndNameField() throws Exception {
        String body = page("SECRTLGT", 1,
                "{\"SECRT_LGT_DVSN_NM\":\"LED 보안등\",\"REFINE_LOTNO_ADDR\":\"경기도 성남시 중원구 1\","
                        + "\"REFINE_WGS84_LAT\":\"37.4300\",\"REFINE_WGS84_LOGT\":\"127.1500\"}");
        SourceReading r = GyeonggiJsonReader.securityLight(url -> body, settings(1000), MAPPER).read();

        assertThat(r.facilities()).singleElement().satisfies(f -> {
            assertThat(f.getFacilityType()).isEqualTo("SECURITY_LIGHT");
            assertThat(f.getDataSource()).isEqualTo("GG_SECURITY_LIGHT");
            assertThat(f.getName()).isEqualTo("LED 보안등");
        });
    }

    @Test
    void httpFailurePropagates() {
        IngestionHttp http = url -> { throw new IOException("HTTP 500"); };
        assertThatThrownBy(() -> GyeonggiJsonReader.cctv(http, settings(1000), MAPPER).read())
                .isInstanceOf(IOException.class);
    }
}
