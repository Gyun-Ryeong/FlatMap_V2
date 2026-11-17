package com.flatmap.navigation.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 경기데이터드림(openapi.gg.go.kr) 차단 감지기 — FlatMap 원본 수집기와 01단계 수집 모듈이 함께 쓴다.
 *
 * 이 호스트는 요청이 몰리면 IP 를 "보안 정책에 의해 차단" 페이지(HTTP 200 의 HTML)로 막는다.
 * 그 페이지를 한 번이라도 받으면 정해진 시간 동안 이 호스트로 나가는 호출을 전부 멈춘다.
 * 차단 중에 계속 두드리면 차단이 길어질 수 있기 때문이다. 상태는 메모리에만 있다(재시작하면 초기화되며,
 * 이후 첫 호출이 차단을 다시 감지한다).
 */
@Component
public class GyeonggiHostGuard {

    private static final Logger log = LoggerFactory.getLogger(GyeonggiHostGuard.class);

    public static final String BASE_URL = "https://openapi.gg.go.kr/";

    private final Duration cooldown;
    private final Clock clock;
    private volatile Instant blockedUntil = Instant.MIN;

    @Autowired
    public GyeonggiHostGuard(@Value("${gyeonggi.block-cooldown-minutes:60}") long cooldownMinutes) {
        this(Duration.ofMinutes(Math.max(1, cooldownMinutes)), Clock.systemUTC());
    }

    GyeonggiHostGuard(Duration cooldown, Clock clock) {
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public static boolean isGyeonggiUrl(String url) {
        return url != null && url.startsWith(BASE_URL);
    }

    /** 차단 페이지(HTML)처럼 보이는가. 정상 응답은 JSON({) 이거나 XML(<?xml, <RESULT, <서비스명>)이다. */
    public static boolean looksBlocked(String body) {
        if (body == null) return false;
        String head = body.length() > 400 ? body.substring(0, 400) : body;
        return head.toLowerCase(Locale.ROOT).contains("<html");
    }

    public boolean isBlocked() {
        return clock.instant().isBefore(blockedUntil);
    }

    /** 차단 대기 중이면 호출하지 않고 예외를 던진다. */
    public void requireOpen() {
        Instant now = clock.instant();
        if (now.isBefore(blockedUntil)) {
            long minutes = Math.max(1, Duration.between(now, blockedUntil).toMinutes() + 1);
            throw new GyeonggiBlockedException("경기데이터드림 차단이 감지되어 약 " + minutes + "분간 호출하지 않습니다");
        }
    }

    /** 응답이 차단 페이지면 대기 시간을 시작하고 예외를 던진다. 아니면 본문을 그대로 돌려준다. */
    public String inspect(String body) {
        if (looksBlocked(body)) {
            markBlocked();
            throw new GyeonggiBlockedException("경기데이터드림이 이 서버의 요청을 차단했습니다(차단 페이지 수신)");
        }
        return body;
    }

    public void markBlocked() {
        blockedUntil = clock.instant().plus(cooldown);
        log.error("[경기] 차단 페이지를 받았습니다. {}분간 openapi.gg.go.kr 호출을 중단합니다.", cooldown.toMinutes());
    }
}
