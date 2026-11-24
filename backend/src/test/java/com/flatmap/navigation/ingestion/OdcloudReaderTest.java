package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OdcloudReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static IngestionSettings settings() {
        return new IngestionSettings(0, 1000, 10, "", "", "od-test-key");
    }

    @Test
    void illuminationHasNoAddressSoOnlyCoordinatesFilter() throws Exception {
        String body = "{\"currentCount\":3,\"totalCount\":3,\"data\":["
                + "{\"위도(LATITUDE)\":\"37.4201\",\"경도(LONGITUDE)\":\"127.1262\"},"
                + "{\"위도(LATITUDE)\":37.7484,\"경도(LONGITUDE)\":127.3536},"      // 가평 → 범위 밖
                + "{\"위도(LATITUDE)\":\"\",\"경도(LONGITUDE)\":\"\"}]}";             // 좌표 없음

        SourceReading r = OdcloudReader.illumination(url -> body, settings(), MAPPER).read();

        assertThat(r.read()).isEqualTo(3);
        assertThat(r.facilities()).singleElement().satisfies(f -> {
            assertThat(f.getDataSource()).isEqualTo("ODCLOUD_ILLUMINATION");
            assertThat(f.getFacilityType()).isEqualTo("SECURITY_LIGHT");
            assertThat(f.getName()).isEqualTo("가로등");
            assertThat(f.getAddress()).isNull();
        });
        assertThat(r.excluded(SourceReading.OUTSIDE_BOUNDS)).isEqualTo(1);
        assertThat(r.excluded(SourceReading.INVALID_COORDS)).isEqualTo(1);
    }

    @Test
    void illuminationRowThatCarriesANonSeongnamAddressIsExcluded() throws Exception {
        String body = "{\"totalCount\":1,\"data\":[{\"위도(LATITUDE)\":\"37.4000\",\"경도(LONGITUDE)\":\"127.1000\","
                + "\"주소\":\"경기도 용인시 수지구 1\"}]}";
        SourceReading r = OdcloudReader.illumination(url -> body, settings(), MAPPER).read();
        assertThat(r.facilities()).isEmpty();
        assertThat(r.excluded(SourceReading.ADDRESS_NOT_SEONGNAM)).isEqualTo(1);
    }

    @Test
    void seongnamCctvRequiresSeongnamAddress() throws Exception {
        String body = "{\"totalCount\":3,\"data\":["
                + "{\"위도\":\"37.3900\",\"경도\":\"127.1100\",\"도로명주소\":\"경기도 성남시 분당구 1\"},"
                + "{\"위도\":\"37.3900\",\"경도\":\"127.1100\",\"도로명주소\":\"\",\"지번주소\":\"경기도 성남시 분당구 2\"},"
                + "{\"위도\":\"37.3900\",\"경도\":\"127.1200\",\"도로명주소\":\"\",\"지번주소\":\"\"}]}";

        SourceReading r = OdcloudReader.seongnamCctv(url -> body, settings(), MAPPER).read();

        assertThat(r.facilities()).hasSize(2);
        assertThat(r.facilities()).allSatisfy(f -> {
            assertThat(f.getFacilityType()).isEqualTo("CCTV");
            assertThat(f.getDataSource()).isEqualTo("ODCLOUD_SEONGNAM_CCTV");
            assertThat(f.getName()).isEqualTo("생활안전CCTV");
        });
        assertThat(r.excluded(SourceReading.ADDRESS_NOT_SEONGNAM)).isEqualTo(1);
    }

    @Test
    void pagesAndEncodesDecodedServiceKey() throws Exception {
        List<String> urls = new ArrayList<>();
        IngestionSettings s = new IngestionSettings(0, 1, 10, "", "", "a+b/c==");
        IngestionHttp http = url -> {
            urls.add(url);
            return url.contains("page=1&")
                    ? "{\"totalCount\":2,\"data\":[{\"위도\":\"37.39\",\"경도\":\"127.11\",\"도로명주소\":\"성남시 A\"}]}"
                    : "{\"totalCount\":2,\"data\":[{\"위도\":\"37.40\",\"경도\":\"127.12\",\"도로명주소\":\"성남시 B\"}]}";
        };

        SourceReading r = OdcloudReader.seongnamCctv(http, s, MAPPER).read();

        assertThat(r.facilities()).hasSize(2);
        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).contains("serviceKey=a%2Bb%2Fc%3D%3D").startsWith("https://api.odcloud.kr/api/15147955/");
    }

    @Test
    void unexpectedShapeFails() {
        assertThatThrownBy(() -> OdcloudReader.illumination(url -> "{\"code\":-4,\"msg\":\"x\"}", settings(), MAPPER).read())
                .isInstanceOf(IngestionException.class);
    }

    @Test
    void missingKeyFails() {
        IngestionSettings noKey = new IngestionSettings(0, 1000, 10, "", "", "");
        assertThatThrownBy(() -> OdcloudReader.illumination(url -> "{}", noKey, MAPPER).read())
                .isInstanceOf(IngestionException.class);
    }
}
