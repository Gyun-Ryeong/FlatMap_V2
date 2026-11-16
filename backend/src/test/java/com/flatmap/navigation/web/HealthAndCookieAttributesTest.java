package com.flatmap.navigation.web;

import com.flatmap.navigation.service.AuthService;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 헬스체크(컨테이너 점검용)와 로그인 쿠키 속성. */
@IntegrationTest
class HealthAndCookieAttributesTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthService authService;

    // ---- 헬스체크 ----

    @Test
    void healthIsUpWithDatabaseCheckedAndNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())      // 세부 내용(DB 종류·경로 등)은 싣지 않는다
                .andExpect(content().string(not(containsString("postgres"))));
    }

    @Test
    void healthProbesAreAvailableForOrchestrators() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/mappings",
                "/actuator/configprops", "/actuator/loggers"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    // ---- 로그인 쿠키 속성 ----

    @Test
    void loginCookieIsHttpOnlySameSiteLaxAndNotSecureOverPlainHttp() throws Exception {
        Mockito.when(authService.loginWithKakao("code")).thenReturn("jwt-value");

        mvc.perform(get("/api/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(cookie().attribute("flatmap_token", "SameSite", "Lax"))
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));   // 테스트(개발) 기본값: HTTP
    }

    @Test
    void logoutCookieUsesTheSameAttributes() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(cookie().attribute("flatmap_token", "SameSite", "Lax"))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }
}
