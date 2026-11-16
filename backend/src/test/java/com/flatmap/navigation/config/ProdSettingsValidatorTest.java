package com.flatmap.navigation.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 운영 프로필 기동 시 설정 검사: 개발용 기본값이 운영에 남으면 뜨지 않아야 하고, 메시지에는 값이 없어야 한다. */
class ProdSettingsValidatorTest {

    private static final String SITE = "https://map.example.org";
    private static final String CALLBACK = "https://map.example.org/api/auth/kakao/callback";
    private static final String JWT = "test-only-jwt-secret-key-0123456789-abcdefghijklmnop";   // 32바이트 이상
    private static final String ADMIN = "test-only-admin-key-0123456789";                      // 16자 이상

    private static List<String> problems(String site, String callback, String jwt, String admin) {
        return ProdSettingsValidator.validate(site, callback, jwt, admin);
    }

    @Test
    void properSettingsPass() {
        assertThat(problems(SITE, CALLBACK, JWT, ADMIN)).isEmpty();
        assertThat(problems("http://10.0.0.5:8080", "http://10.0.0.5/api/auth/kakao/callback", JWT, ADMIN)).isEmpty();
    }

    @Test
    void developmentDefaultsAreRejected() {
        // 개발용 기본값 그대로(localhost 주소, 빈 관리 키)
        List<String> p = problems("http://localhost:3000", "http://localhost:8080/api/auth/kakao/callback", JWT, "");
        assertThat(p).hasSize(3);
        assertThat(String.join(" ", p)).contains("FRONTEND_URL", "KAKAO_OAUTH_REDIRECT_URI", "ADMIN_API_KEY");
    }

    @Test
    void localHostsInEveryFormAreRejected() {
        for (String host : new String[]{"http://localhost", "http://localhost:3000/", "https://127.0.0.1:8443", "http://0.0.0.0",
                "http://[::1]:3000", "http://[::1]", "http://dev.localhost:3000", "HTTP://LOCALHOST:3000"}) {
            assertThat(problems(host, CALLBACK, JWT, ADMIN)).as(host).hasSize(1);
        }
        // localhost 로 시작하는 진짜 도메인은 막지 않는다
        assertThat(problems("https://localhost-map.example.org", CALLBACK, JWT, ADMIN)).isEmpty();
    }

    @Test
    void missingOrNonHttpUrlsAreRejected() {
        assertThat(problems(null, CALLBACK, JWT, ADMIN)).hasSize(1);
        assertThat(problems("", CALLBACK, JWT, ADMIN)).hasSize(1);
        assertThat(problems("   ", CALLBACK, JWT, ADMIN)).hasSize(1);
        assertThat(problems("map.example.org", CALLBACK, JWT, ADMIN)).hasSize(1);       // 스킴 없음
        assertThat(problems("ftp://map.example.org", CALLBACK, JWT, ADMIN)).hasSize(1);
        assertThat(problems("https://", CALLBACK, JWT, ADMIN)).hasSize(1);               // 호스트 없음
    }

    @Test
    void shortSecretsAreRejectedAtTheBoundary() {
        assertThat(problems(SITE, CALLBACK, "x".repeat(31), ADMIN)).hasSize(1);
        assertThat(problems(SITE, CALLBACK, "x".repeat(32), ADMIN)).isEmpty();
        assertThat(problems(SITE, CALLBACK, JWT, "y".repeat(15))).hasSize(1);
        assertThat(problems(SITE, CALLBACK, JWT, "y".repeat(16))).isEmpty();
        assertThat(problems(SITE, CALLBACK, null, null)).hasSize(2);
        // 공백만 있는 관리 키는 키가 아니다
        assertThat(problems(SITE, CALLBACK, JWT, " ".repeat(20))).hasSize(1);
    }

    @Test
    void failureStopsStartupAndNeverEchoesTheValues() {
        ProdSettingsValidator bad = new ProdSettingsValidator("http://localhost:3000", CALLBACK,
                "tiny-secret-SHOULD-NOT-LEAK", "adm-SECRET-9");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(bad, "check"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_URL").hasMessageContaining("JWT_SECRET").hasMessageContaining("ADMIN_API_KEY")
                .hasMessageNotContaining("SHOULD-NOT-LEAK").hasMessageNotContaining("adm-SECRET-9").hasMessageNotContaining("http://localhost:3000");
        // 올바른 설정은 예외 없이 지나간다
        new ProdSettingsValidator(SITE, CALLBACK, JWT, ADMIN).check();
    }
}
