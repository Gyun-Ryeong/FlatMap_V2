package com.flatmap.navigation.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로그인 쿠키(JWT) 발급·검증. 비밀값은 테스트용 가짜 값이다(32바이트 이상). */
class JwtUtilTest {

    private static final String SECRET = "test-only-jwt-secret-key-0123456789-abcdefghijklmnop";
    private static final String OTHER_SECRET = "test-only-other-secret-key-0123456789-abcdefghijklm";

    private static JwtUtil util(String secret, long expirationMs) {
        JwtUtil u = new JwtUtil();
        ReflectionTestUtils.setField(u, "secret", secret);
        ReflectionTestUtils.setField(u, "expirationMs", expirationMs);
        return u;
    }

    @Test
    void issuedTokenRoundTripsTheUserId() {
        JwtUtil u = util(SECRET, 60_000);
        String token = u.generateToken(42L);

        assertThat(u.validateToken(token)).isTrue();
        assertThat(u.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = util(OTHER_SECRET, 60_000).generateToken(1L);

        assertThat(util(SECRET, 60_000).validateToken(foreign)).isFalse();
        assertThatThrownBy(() -> util(SECRET, 60_000).getUserIdFromToken(foreign)).isInstanceOf(Exception.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtUtil u = util(SECRET, 60_000);
        String[] parts = u.generateToken(1L).split("\\.");
        // 다른 사용자(2)의 페이로드로 바꿔 서명은 그대로 둔다
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"2\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(u.validateToken(forged)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil u = util(SECRET, -1_000);                 // 이미 만료된 상태로 발급
        assertThat(u.validateToken(u.generateToken(1L))).isFalse();
    }

    @Test
    void garbageAndEmptyTokensAreRejectedWithoutThrowing() {
        JwtUtil u = util(SECRET, 60_000);
        assertThat(u.validateToken("")).isFalse();
        assertThat(u.validateToken("not-a-jwt")).isFalse();
        assertThat(u.validateToken("a.b.c")).isFalse();
    }

    @Test
    void unsignedTokenIsRejected() {
        // alg=none 으로 서명 없이 만든 토큰은 받아들이지 않는다
        String unsigned = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        assertThat(util(SECRET, 60_000).validateToken(unsigned)).isFalse();
    }

    @Test
    void tokenWithAShortKeyCannotBeIssuedByMistake() {
        // 키가 32바이트 미만이면 HS256 서명 자체를 거부한다(약한 키로 조용히 동작하지 않는다)
        assertThatThrownBy(() -> util("short", 60_000).generateToken(1L)).isInstanceOf(Exception.class);
        SecretKey ok = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        assertThat(ok.getEncoded()).hasSizeGreaterThanOrEqualTo(32);
    }
}
