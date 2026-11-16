package com.flatmap.navigation.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 성남시 범위 판정: 좌표 사각형은 경계를 포함하고, 인접 시군은 주소 검사가 거른다. */
class SeongnamFilterTest {

    @Test
    void boundsAreInclusiveOnAllFourEdges() {
        assertThat(SeongnamFilter.inBounds(37.33, 127.04)).isTrue();
        assertThat(SeongnamFilter.inBounds(37.53, 127.20)).isTrue();
        assertThat(SeongnamFilter.inBounds(37.33, 127.20)).isTrue();
        assertThat(SeongnamFilter.inBounds(37.53, 127.04)).isTrue();
    }

    @Test
    void justOutsideEachEdgeIsRejected() {
        assertThat(SeongnamFilter.inBounds(Math.nextDown(37.33), 127.10)).isFalse();
        assertThat(SeongnamFilter.inBounds(Math.nextUp(37.53), 127.10)).isFalse();
        assertThat(SeongnamFilter.inBounds(37.40, Math.nextDown(127.04))).isFalse();
        assertThat(SeongnamFilter.inBounds(37.40, Math.nextUp(127.20))).isFalse();
    }

    @Test
    void seoulAndSwappedCoordinatesAreRejected() {
        assertThat(SeongnamFilter.inBounds(37.5665, 126.9780)).isFalse();   // 서울 시청
        assertThat(SeongnamFilter.inBounds(127.10, 37.40)).isFalse();       // 위도·경도를 바꿔 넣음
    }

    @Test
    void addressMustMentionSeongnam() {
        assertThat(SeongnamFilter.isSeongnamAddress("경기도 성남시 분당구 정자동 1")).isTrue();
        assertThat(SeongnamFilter.isSeongnamAddress("경기도 광주시 오포읍")).isFalse();
        assertThat(SeongnamFilter.isSeongnamAddress("")).isFalse();
        assertThat(SeongnamFilter.isSeongnamAddress(null)).isFalse();
    }

    @Test
    void boundsConstantsAreTheDocumentedValues() {
        // 검증 SQL(docs/dev/ingestion-check.md)과 같은 값이다
        assertThat(SeongnamFilter.MIN_LAT).isEqualTo(37.33);
        assertThat(SeongnamFilter.MAX_LAT).isEqualTo(37.53);
        assertThat(SeongnamFilter.MIN_LNG).isEqualTo(127.04);
        assertThat(SeongnamFilter.MAX_LNG).isEqualTo(127.20);
    }
}
