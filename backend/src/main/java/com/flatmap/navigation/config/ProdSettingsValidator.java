package com.flatmap.navigation.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 운영 프로필(prod)에서만 도는 기동 시 설정 검사. 개발용 기본값(localhost 주소, 빈 관리 키, 짧은 JWT 비밀)이
 * 운영에 그대로 남아 조용히 기동되는 것을 막는다. 문제가 있으면 애플리케이션이 뜨지 않는다.
 *
 * <p>오류 메시지에는 설정 <b>이름</b>만 쓰고 값은 쓰지 않는다(비밀값이 로그에 남지 않게).
 */
@Configuration
@Profile("prod")
public class ProdSettingsValidator {

    static final int MIN_JWT_SECRET_BYTES = 32;
    static final int MIN_ADMIN_KEY_CHARS = 16;

    private final String frontendUrl;
    private final String kakaoRedirectUri;
    private final String jwtSecret;
    private final String adminApiKey;

    public ProdSettingsValidator(@Value("${app.frontend-url}") String frontendUrl,
                                 @Value("${kakao.oauth.redirect-uri}") String kakaoRedirectUri,
                                 @Value("${jwt.secret}") String jwtSecret,
                                 @Value("${admin.api-key:}") String adminApiKey) {
        this.frontendUrl = frontendUrl;
        this.kakaoRedirectUri = kakaoRedirectUri;
        this.jwtSecret = jwtSecret;
        this.adminApiKey = adminApiKey;
    }

    @PostConstruct
    void check() {
        List<String> problems = validate(frontendUrl, kakaoRedirectUri, jwtSecret, adminApiKey);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("운영 설정이 올바르지 않습니다: " + String.join("; ", problems));
        }
    }

    /** 문제 목록(설정 이름과 이유만). 비어 있으면 통과. */
    static List<String> validate(String frontendUrl, String kakaoRedirectUri, String jwtSecret, String adminApiKey) {
        List<String> problems = new ArrayList<>();
        if (!isPublicHttpUrl(frontendUrl)) {
            problems.add("FRONTEND_URL 은 localhost 가 아닌 http(s) 주소여야 합니다");
        }
        if (!isPublicHttpUrl(kakaoRedirectUri)) {
            problems.add("KAKAO_OAUTH_REDIRECT_URI 는 localhost 가 아닌 http(s) 주소여야 합니다");
        }
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET 은 " + MIN_JWT_SECRET_BYTES + "바이트 이상이어야 합니다");
        }
        if (adminApiKey == null || adminApiKey.strip().length() < MIN_ADMIN_KEY_CHARS) {
            problems.add("ADMIN_API_KEY 는 " + MIN_ADMIN_KEY_CHARS + "자 이상이어야 합니다");
        }
        return problems;
    }

    private static boolean isPublicHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String u = url.strip().toLowerCase();
        if (!(u.startsWith("http://") || u.startsWith("https://"))) return false;
        String host = u.substring(u.indexOf("://") + 3);
        if (host.startsWith("[")) {                       // IPv6 리터럴: 대괄호 안쪽 전체가 호스트
            int close = host.indexOf(']');
            host = close < 0 ? host : host.substring(0, close + 1);
        } else {
            int end = host.length();
            for (char c : new char[]{'/', ':', '?', '#'}) {
                int i = host.indexOf(c);
                if (i >= 0) end = Math.min(end, i);
            }
            host = host.substring(0, end);
        }
        return !host.isEmpty() && !host.equals("localhost") && !host.equals("127.0.0.1")
                && !host.equals("0.0.0.0") && !host.equals("[::1]") && !host.endsWith(".localhost");
    }
}
