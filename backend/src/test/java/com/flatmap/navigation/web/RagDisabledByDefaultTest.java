package com.flatmap.navigation.web;

import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.rag.RagClient;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기본 설정(rag.enabled=false)에서는 RAG 관련 빈도 엔드포인트도 없다. RAG 는 개발 중이며 기능으로 노출하지 않는다.
 */
@IntegrationTest
class RagDisabledByDefaultTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ApplicationContext context;
    @Autowired
    Environment environment;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void theDefaultIsOff() {
        // 설정 파일의 기본값 자체가 false 다(환경변수가 없을 때)
        assertThat(environment.getProperty("rag.enabled", Boolean.class, false)).isFalse();
    }

    @Test
    void noRagBeanIsRegistered() {
        assertThat(context.getBeansOfType(RagClient.class)).isEmpty();
        assertThat(context.containsBean("ragController")).isFalse();
        assertThat(context.containsBean("notConnectedRagClient")).isFalse();
    }

    @Test
    void ragEndpointsDoNotExistEvenForALoggedInUser() throws Exception {
        Cookie login = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtUtil.generateToken(1L));

        mvc.perform(get("/api/rag/status")).andExpect(status().isNotFound());
        mvc.perform(get("/api/rag/status").cookie(login)).andExpect(status().isNotFound());
        mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"안녕\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/rag/ask").cookie(login).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"안녕\"}"))
                .andExpect(status().isNotFound());
    }
}
