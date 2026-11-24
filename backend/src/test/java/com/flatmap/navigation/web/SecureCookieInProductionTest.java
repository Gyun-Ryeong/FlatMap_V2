package com.flatmap.navigation.web;

import com.flatmap.navigation.service.AuthService;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 운영(HTTPS)에서는 app.cookie-secure=true 로 로그인 쿠키에 Secure 가 붙는다. */
@IntegrationTest
@TestPropertySource(properties = "app.cookie-secure=true")
class SecureCookieInProductionTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthService authService;

    @Test
    void loginAndLogoutCookiesCarrySecureWhenEnabled() throws Exception {
        Mockito.when(authService.loginWithKakao("code")).thenReturn("jwt-value");

        mvc.perform(get("/api/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(cookie().attribute("flatmap_token", "SameSite", "Lax"));
        mvc.perform(post("/api/auth/logout"))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }
}
