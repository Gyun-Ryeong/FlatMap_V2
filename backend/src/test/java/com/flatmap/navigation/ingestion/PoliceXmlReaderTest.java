package com.flatmap.navigation.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliceXmlReaderTest {

    private static String row(String div, String name, String addr, String lat, String lng) {
        return "<row><FACLT_DIV_NM>" + div + "</FACLT_DIV_NM><INST_NM>" + name + "</INST_NM>"
                + "<REFINE_ROADNM_ADDR>" + addr + "</REFINE_ROADNM_ADDR>"
                + "<REFINE_WGS84_LAT>" + lat + "</REFINE_WGS84_LAT><REFINE_WGS84_LOGT>" + lng + "</REFINE_WGS84_LOGT></row>";
    }

    private static String xml(int total, String... rows) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><FiresttnPolcsttnM><head><list_total_count>" + total
                + "</list_total_count><RESULT><CODE>INFO-000</CODE></RESULT></head>" + String.join("", rows)
                + "</FiresttnPolcsttnM>";
    }

    private static IngestionSettings settings() {
        return new IngestionSettings(0, 1000, 10, "", "gg-test-key", "");
    }

    @Test
    void keepsPoliceFacilitiesAndExcludesFireStationsWithCount() throws Exception {
        String body = xml(7,
                row("경찰서", "성남중원경찰서", "경기도 성남시 중원구 1", "37.4300", "127.1400"),
                row("지구대", "수진지구대", "경기도 성남시 수정구 2", "37.4400", "127.1300"),
                row("파출소", "야탑파출소", "경기도 성남시 분당구 3", "37.4100", "127.1300"),
                row("치안센터", "서현치안센터", "경기도 성남시 분당구 4", "37.3850", "127.1230"),
                row("소방서", "성남소방서", "경기도 성남시 수정구 5", "37.4500", "127.1400"),         // 소방 → 제외
                row("안전센터", "119안전센터", "경기도 성남시 분당구 6", "37.4000", "127.1200"),     // 소방(119) → 제외
                row("기타", "어떤시설", "경기도 성남시 분당구 7", "37.4000", "127.1100"));           // 경찰 아님 → 제외

        SourceReading r = new PoliceXmlReader(url -> body, settings()).read();

        assertThat(r.read()).isEqualTo(7);
        assertThat(r.facilities()).extracting("name")
                .containsExactly("성남중원경찰서", "수진지구대", "야탑파출소", "서현치안센터");
        assertThat(r.facilities()).allSatisfy(f -> {
            assertThat(f.getFacilityType()).isEqualTo("POLICE");
            assertThat(f.getDataSource()).isEqualTo("GG_POLICE");
        });
        assertThat(r.excluded(SourceReading.FIRE_STATION)).isEqualTo(2);
        assertThat(r.excluded(SourceReading.NOT_POLICE)).isEqualTo(1);
    }

    @Test
    void policeOutsideSeongnamIsExcluded() throws Exception {
        String body = xml(2,
                row("지구대", "가평지구대", "경기도 가평군 가평읍 1", "37.8300", "127.5100"),
                row("지구대", "용인지구대", "경기도 용인시 처인구 1", "37.4100", "127.1000"));

        SourceReading r = new PoliceXmlReader(url -> body, settings()).read();

        assertThat(r.facilities()).isEmpty();
        assertThat(r.excluded(SourceReading.OUTSIDE_BOUNDS)).isEqualTo(1);
        assertThat(r.excluded(SourceReading.ADDRESS_NOT_SEONGNAM)).isEqualTo(1);
    }

    @Test
    void classifyChecksFireBeforePolice() {
        assertThat(PoliceXmlReader.classify("소방서", "성남소방서")).isEqualTo(PoliceXmlReader.Kind.FIRE);
        assertThat(PoliceXmlReader.classify("", "119안전센터")).isEqualTo(PoliceXmlReader.Kind.FIRE);
        assertThat(PoliceXmlReader.classify("경찰서", "성남수정경찰서")).isEqualTo(PoliceXmlReader.Kind.POLICE);
        assertThat(PoliceXmlReader.classify(null, "치안센터")).isEqualTo(PoliceXmlReader.Kind.POLICE);
        assertThat(PoliceXmlReader.classify("복지", "주민센터")).isEqualTo(PoliceXmlReader.Kind.OTHER);
    }

    @Test
    void noDataResultEndsWithoutError() throws Exception {
        String body = "<RESULT><CODE>INFO-200</CODE><MESSAGE>해당하는 데이터가 없습니다.</MESSAGE></RESULT>";
        assertThat(new PoliceXmlReader(url -> body, settings()).read().facilities()).isEmpty();
    }

    @Test
    void errorResultFailsWithCodeOnly() {
        String body = "<RESULT><CODE>ERROR-300</CODE><MESSAGE>필수 값이 누락 gg-test-key</MESSAGE></RESULT>";
        assertThatThrownBy(() -> new PoliceXmlReader(url -> body, settings()).read())
                .isInstanceOf(IngestionException.class)
                .hasMessage("API result code ERROR-300");
    }

    @Test
    void doctypeIsRejectedToBlockXxe() {
        String body = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertThatThrownBy(() -> new PoliceXmlReader(url -> body, settings()).read())
                .isNotInstanceOf(IngestionException.class); // 파서가 거부한다(SAXParseException)
    }
}
