package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextQualityTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "?", "???", " ? ? ", "\t", "경기도 ��", "�"})
    void badValues(String v) {
        assertThat(TextQuality.isBad(v)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"성남시 분당구 정자동", "CCTV", "상면876 TS", "a?b", "Seongnam 1"})
    void goodValues(String v) {
        assertThat(TextQuality.isBad(v)).isFalse();
    }

    private static List<SafetyFacility> rows(int good, int bad) {
        List<SafetyFacility> list = new ArrayList<>();
        for (int i = 0; i < good; i++) list.add(new SafetyFacility("CCTV", "정상", "성남시 어딘가", null, 37.4, 127.1, "T"));
        for (int i = 0; i < bad; i++) list.add(new SafetyFacility("CCTV", "???", "성남시 어딘가", null, 37.4, 127.1, "T"));
        return list;
    }

    @Test
    void limitIsExactlyFivePercentExclusive() {
        // 5% 정확히는 통과, 초과해야 실패
        assertThat(TextQuality.badRatio(rows(95, 5), true)).isEqualTo(0.05);
        assertThat(TextQuality.exceedsLimit(5, 100)).isFalse();
        assertThat(TextQuality.exceedsLimit(6, 100)).isTrue();
        assertThat(TextQuality.exceedsLimit(0, 0)).isFalse();
    }

    @Test
    void addressIsIgnoredWhenNotChecked() {
        SafetyFacility noAddress = new SafetyFacility("SECURITY_LIGHT", "가로등", null, null, 37.4, 127.1, "T");
        assertThat(TextQuality.isBadRow(noAddress, true)).isTrue();
        assertThat(TextQuality.isBadRow(noAddress, false)).isFalse();
    }
}
