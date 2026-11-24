package com.flatmap.navigation.service;

import com.flatmap.navigation.model.SteepSlopeArea;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 번들 CSV 를 읽는 순수 로직(DB 없음). 적재 자체는 통합 테스트가 본다. */
class SteepSlopeGradeImportServiceTest {

    private static final String HEADER =
            "급경사지명,시군구,주소,산주소여부,위도,경도,지오코딩방식," +
            "경사도_지점(30m·도),경사도_3x3최대(90m·도),경사도_4x4최대(120m·도),경사도_5x5최대(150m·도),최종경사도(도),값출처\n";

    private static String row(String name, String sigungu, String address, String mountain,
                              String lat, String lng, String grade) {
        return "\"" + name.replace("\"", "\"\"") + "\"," + sigungu + ",\"" + address + "\"," + mountain + "," + lat + "," + lng +
                ",address,1,2,3,4," + grade + ",지점\n";
    }

    private static List<SteepSlopeArea> parse(String csv) {
        return SteepSlopeGradeImportService.parse(new StringReader(csv));
    }

    @Test
    void parsesNameLocationRegionAddressPartsAndGradeInDegrees() {
        List<SteepSlopeArea> areas = parse(HEADER +
                row("경기 성남 수정 태평 N2지구", "수정구", "경기도 성남시 수정구 태평동 224-11", "", "37.4510518747915", "127.136677427918", "12.05"));

        assertThat(areas).hasSize(1);
        SteepSlopeArea a = areas.get(0);
        assertThat(a.getName()).isEqualTo("경기 성남 수정 태평 N2지구");
        assertThat(a.getLatitude()).isEqualTo(37.4510518747915);
        assertThat(a.getLongitude()).isEqualTo(127.136677427918);
        assertThat(a.getGrade()).isEqualTo(12.05);
        assertThat(a.getSource()).isEqualTo("MOIS_CSV");
        assertThat(a.getRegionCode()).isEqualTo("41131");
        assertThat(a.getSigungu()).isEqualTo("성남시 수정구");
        assertThat(a.getEupmyeon()).isEqualTo("태평동");
        assertThat(a.getJibun()).isEqualTo("224-11");
        assertThat(a.getIsMountain()).isFalse();
    }

    @Test
    void mountainFlagAndRegionCodesFollowTheRow() {
        List<SteepSlopeArea> areas = parse(HEADER +
                row("갈현", "중원구", "경기도 성남시 중원구 갈현동 산129-3", "Y", "37.45", "127.17", "19.31") +
                row("야탑", "분당구", "경기도 성남시 분당구 야탑동 1", "", "37.41", "127.13", "14.76"));

        assertThat(areas.get(0).getIsMountain()).isTrue();
        assertThat(areas.get(0).getJibun()).isEqualTo("산129-3");
        assertThat(areas.get(0).getRegionCode()).isEqualTo("41133");
        assertThat(areas.get(1).getIsMountain()).isFalse();
        assertThat(areas.get(1).getRegionCode()).isEqualTo("41135");
    }

    @Test
    void ignoresByteOrderMarkAndHandlesWindowsLineEndings() {
        String csv = "﻿" + (HEADER +
                row("단파길", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "17.78")).replace("\n", "\r\n");

        assertThat(parse(csv)).extracting(SteepSlopeArea::getName).containsExactly("단파길");
    }

    @Test
    void keepsCommaAndQuoteInsideQuotedName() {
        List<SteepSlopeArea> areas = parse(HEADER +
                row("이름, \"쉼표\" 포함", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "5"));

        assertThat(areas.get(0).getName()).isEqualTo("이름, \"쉼표\" 포함");
    }

    @Test
    void zeroDegreesIsAValidMeasuredValueNotMissing() {
        List<SteepSlopeArea> areas = parse(HEADER +
                row("평지", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "0"));

        assertThat(areas.get(0).getGrade()).isEqualTo(0.0);
    }

    @Test
    void rejectsGradeOutsideZeroToNinetyDegrees() {
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "90")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("90");
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericGradeAndCoordinates() {
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "abc")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("최종경사도");
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "수정구", "경기도 성남시 수정구 단대동 193", "", "", "127.15", "5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("위도");
    }

    @Test
    void rejectsUnparsableAddressOrSigunguThatDisagreesWithIt() {
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "수정구", "서울특별시 종로구 창신동 1", "", "37.45", "127.15", "5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("주소");
        assertThatThrownBy(() -> parse(HEADER +
                row("a", "분당구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("시군구");
    }

    @Test
    void rejectsMissingColumnEmptyNameAndEmptyFile() {
        assertThatThrownBy(() -> parse("급경사지명,시군구\nx,수정구\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("필수 열");
        assertThatThrownBy(() -> parse(HEADER +
                row("  ", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("급경사지명");
        assertThatThrownBy(() -> parse(HEADER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneBadRowFailsTheWholeFileInsteadOfPartialResult() {
        assertThatThrownBy(() -> parse(HEADER +
                row("정상", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "5") +
                row("불량", "수정구", "경기도 성남시 수정구 단대동 193", "", "37.45", "127.15", "200")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- 번들 CSV (원본 FlatMap 값) -----

    private static List<SteepSlopeArea> bundled() throws Exception {
        try (InputStream in = SteepSlopeGradeImportService.class.getResourceAsStream(
                SteepSlopeGradeImportService.RESOURCE_PATH)) {
            assertThat(in).as("번들 CSV").isNotNull();
            return SteepSlopeGradeImportService.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bundledCsvHas84RowsAllWithGradeInDegrees() throws Exception {
        List<SteepSlopeArea> areas = bundled();

        assertThat(areas).hasSize(84);
        assertThat(areas).allSatisfy(a -> {
            assertThat(a.getGrade()).isNotNull().isGreaterThan(0.0).isLessThan(90.0);
            assertThat(a.getSource()).isEqualTo("MOIS_CSV");
            assertThat(a.getRegionCode()).isIn("41131", "41133", "41135");
            assertThat(a.getLatitude()).isBetween(37.3, 37.5);
            assertThat(a.getLongitude()).isBetween(127.0, 127.2);
        });
    }

    @Test
    void bundledCsvKeepsOriginalFlatMapValues() throws Exception {
        List<SteepSlopeArea> areas = bundled();

        // 원본 FlatMap 덤프(2026-11-07)의 값과 같다: 지점 값, 3x3·5x5 보정 없이 그대로.
        assertThat(grade(areas, "경기 성남 수정 태평 N2지구")).isEqualTo(12.05);
        assertThat(grade(areas, "경기 성남 수정 태평 N3지구")).isEqualTo(3.44);
        assertThat(grade(areas, "경기 성남 수정 창곡 N1지구")).isEqualTo(24.29);
        assertThat(areas.stream().mapToDouble(SteepSlopeArea::getGrade).max().orElseThrow()).isEqualTo(27.02);
        assertThat(areas.stream().mapToDouble(SteepSlopeArea::getGrade).min().orElseThrow()).isEqualTo(0.95);
    }

    @Test
    void bundledCsvMarksMountainLotsAndKeepsDuplicateNamesAsSeparateRows() throws Exception {
        List<SteepSlopeArea> areas = bundled();

        assertThat(areas.stream().filter(a -> a.getName().equals("이배재고개")).findFirst().orElseThrow()
                .getIsMountain()).isTrue();
        assertThat(areas.stream().filter(a -> a.getName().equals("소각장"))).hasSize(2);
    }

    private static double grade(List<SteepSlopeArea> areas, String name) {
        return areas.stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow().getGrade();
    }
}
