package com.flatmap.navigation.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeongnamCsvReaderTest {

    private static final Charset CP949 = Charset.forName("x-windows-949");
    private static final String HEADER =
            "보안등위치명,설치개수,소재지도로명주소,소재지지번주소,위도,경도,설치연도,설치형태,관리기관전화번호,관리기관명,데이터기준일자\n";

    @TempDir
    Path dir;

    private IngestionSettings settingsFor(Path csv) {
        return new IngestionSettings(0, 1000, 10, csv.toString(), "", "");
    }

    private Path write(String content, Charset cs) throws Exception {
        Path p = dir.resolve("lights.csv");
        Files.write(p, content.getBytes(cs));
        return p;
    }

    @Test
    void readsCp949KoreanAndFiltersToSeongnam() throws Exception {
        String csv = HEADER
                + "정자1동 001,1,경기도 성남시 분당구 불정로 1,경기도 성남시 분당구 정자동 1,37.3650,127.1080,2013,한전주,031-729-0000,경기도 성남시청,2026-01-06\n"
                + "수진 002,1,,경기도 성남시 수정구 수진동 2,37.4380,127.1370,2014,한전주,031-729-0000,경기도 성남시청,2026-01-06\n"
                + "\"쉼표,포함 003\",1,\"경기도 성남시 중원구 성남대로, 3\",,37.4300,127.1500,2015,한전주,031-729-0000,경기도 성남시청,2026-01-06\n"
                + "상면876 TS,1,,경기도 가평군 상면 행현리663-19,37.748447,127.353638,2013,한전주,031-580-2102,경기도 가평군청,2026-01-06\n"
                + "수지 004,1,,경기도 용인시 수지구 풍덕천동 4,37.3400,127.1000,2013,한전주,031-000-0000,경기도 용인시청,2026-01-06\n"
                + "좌표없음 005,1,,경기도 성남시 분당구 5,,,2013,한전주,031-729-0000,경기도 성남시청,2026-01-06\n";
        Path csvFile = write(csv, CP949);

        SourceReading r = new SeongnamCsvReader(settingsFor(csvFile)).read();

        assertThat(r.read()).isEqualTo(6);
        assertThat(r.facilities()).extracting("name")
                .containsExactly("정자1동 001", "수진 002", "쉼표,포함 003");
        assertThat(r.facilities()).extracting("address")
                .containsExactly("경기도 성남시 분당구 불정로 1",          // 도로명 우선
                        "경기도 성남시 수정구 수진동 2",                      // 도로명이 없으면 지번
                        "경기도 성남시 중원구 성남대로, 3");                  // 따옴표 안 쉼표
        assertThat(r.facilities()).allSatisfy(f -> {
            assertThat(f.getDataSource()).isEqualTo("SEONGNAM_CSV");
            assertThat(f.getFacilityType()).isEqualTo("SECURITY_LIGHT");
            assertThat(TextQuality.isBadRow(f, true)).isFalse();
        });
        assertThat(r.excluded(SourceReading.OUTSIDE_BOUNDS)).isEqualTo(1);        // 가평
        assertThat(r.excluded(SourceReading.ADDRESS_NOT_SEONGNAM)).isEqualTo(1);  // 용인
        assertThat(r.excluded(SourceReading.INVALID_COORDS)).isEqualTo(1);        // 좌표 없음
    }

    @Test
    void utf8EncodedFileIsRejectedInsteadOfLoadingGarbage() throws Exception {
        // CP949 로 읽을 파일이 UTF-8 이면 헤더가 맞지 않는다 → 깨진 한글을 적재하지 않고 실패한다.
        Path csvFile = write(HEADER + "정자1동 001,1,경기도 성남시 분당구 1,,37.3650,127.1080,2013,x,y,z,w\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new SeongnamCsvReader(settingsFor(csvFile)).read())
                .isInstanceOf(IngestionException.class)
                .hasMessage("csv header mismatch");
    }

    @Test
    void findsColumnsByHeaderNameRegardlessOfOrder() throws Exception {
        // 컬럼 순서가 달라도 헤더 이름으로 찾는다.
        String csv = "위도,경도,보안등위치명,소재지지번주소,소재지도로명주소\n"
                + "37.3650,127.1080,정자 가로등,경기도 성남시 분당구 정자동 1,\n";
        SourceReading r = new SeongnamCsvReader(settingsFor(write(csv, CP949))).read();
        assertThat(r.facilities()).singleElement().satisfies(f -> {
            assertThat(f.getName()).isEqualTo("정자 가로등");
            assertThat(f.getAddress()).isEqualTo("경기도 성남시 분당구 정자동 1");
        });
    }

    @Test
    void missingPathOrFileFails() {
        assertThatThrownBy(() -> new SeongnamCsvReader(new IngestionSettings(0, 1, 1, "  ", "", "")).read())
                .isInstanceOf(IngestionException.class).hasMessage("csv path not configured");
        assertThatThrownBy(() -> new SeongnamCsvReader(settingsFor(dir.resolve("nope.csv"))).read())
                .isInstanceOf(IngestionException.class).hasMessage("csv file not found");
    }

    @Test
    void csvLineSplitHandlesQuotes() {
        assertThat(CsvLine.split("a,\"b,c\",\"d\"\"e\",,f")).containsExactly("a", "b,c", "d\"e", "", "f");
    }
}
