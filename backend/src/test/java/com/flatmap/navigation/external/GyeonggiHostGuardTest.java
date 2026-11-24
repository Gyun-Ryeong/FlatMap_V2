package com.flatmap.navigation.external;

import com.flatmap.navigation.ingestion.JdkIngestionHttp;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 네트워크 호출 없음. */
class GyeonggiHostGuardTest {

    /** 실제로 받은 차단 응답의 모양(EUC-KR HTML 을 UTF-8 로 읽으면 한글이 깨진다). */
    private static final String BLOCK_PAGE = "<html><head><meta charset=\"euc-kr\"></head><body bgcolor=#ffffff><br>"
            + "<CENTER><font face=�� size=4><b><hr> - ��� ���</b></font></CENTER></body></html>";

    /** 테스트에서 시간을 직접 움직이는 시계. */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-17T01:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void recognizesBlockPageButNotNormalResponses() {
        assertThat(GyeonggiHostGuard.looksBlocked(BLOCK_PAGE)).isTrue();
        assertThat(GyeonggiHostGuard.looksBlocked("  \n<HTML>...")).isTrue();
        // 정상 JSON·XML 응답, 빈 값은 차단이 아니다.
        assertThat(GyeonggiHostGuard.looksBlocked("{\"SenircentFaclt\":[{\"head\":[]}]}")).isFalse();
        assertThat(GyeonggiHostGuard.looksBlocked("{\"RESULT\":{\"CODE\":\"INFO-200\"}}")).isFalse();
        assertThat(GyeonggiHostGuard.looksBlocked("<?xml version=\"1.0\" encoding=\"UTF-8\"?><SECRTLGT><head/></SECRTLGT>")).isFalse();
        assertThat(GyeonggiHostGuard.looksBlocked("")).isFalse();
        assertThat(GyeonggiHostGuard.looksBlocked(null)).isFalse();
    }

    @Test
    void blockPageStartsCooldownAndStopsFurtherCallsUntilItEnds() {
        MutableClock clock = new MutableClock();
        GyeonggiHostGuard guard = new GyeonggiHostGuard(Duration.ofMinutes(60), clock);

        assertThat(guard.isBlocked()).isFalse();
        assertThatCode(guard::requireOpen).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.inspect(BLOCK_PAGE)).isInstanceOf(GyeonggiBlockedException.class);
        assertThat(guard.isBlocked()).isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(59));
        assertThatThrownBy(guard::requireOpen).isInstanceOf(GyeonggiBlockedException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(2)); // 61분 경과
        assertThat(guard.isBlocked()).isFalse();
        assertThatCode(guard::requireOpen).doesNotThrowAnyException();
    }

    @Test
    void normalBodyPassesThroughUnchangedAndDoesNotBlock() {
        GyeonggiHostGuard guard = new GyeonggiHostGuard(Duration.ofMinutes(60), new MutableClock());
        String json = "{\"a\":1}";
        assertThat(guard.inspect(json)).isSameAs(json);
        assertThat(guard.isBlocked()).isFalse();
    }

    @Test
    void identifiesGyeonggiHostOnly() {
        assertThat(GyeonggiHostGuard.isGyeonggiUrl("https://openapi.gg.go.kr/CCTV?KEY=x")).isTrue();
        assertThat(GyeonggiHostGuard.isGyeonggiUrl("https://api.odcloud.kr/api/x")).isFalse();
        assertThat(GyeonggiHostGuard.isGyeonggiUrl(null)).isFalse();
    }

    @Test
    void ingestionHttpDoesNotTouchTheNetworkWhileGyeonggiIsBlocked() {
        GyeonggiHostGuard guard = new GyeonggiHostGuard(Duration.ofMinutes(60), new MutableClock());
        guard.markBlocked();
        JdkIngestionHttp http = new JdkIngestionHttp(1, 1, guard);

        // 요청을 만들기 전에 예외가 난다 — 네트워크로 나가지 않는다는 뜻이다. 메시지에 URL·키가 없어야 한다.
        assertThatThrownBy(() -> http.get("https://openapi.gg.go.kr/CCTV?KEY=secret-key-value&Type=json"))
                .isInstanceOf(GyeonggiBlockedException.class)
                .hasMessageNotContaining("secret-key-value")
                .hasMessageNotContaining("openapi.gg.go.kr");
    }
}
