package com.flatmap.navigation.web;

import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.rag.NotConnectedRagClient;
import com.flatmap.navigation.rag.RagClient;
import com.flatmap.navigation.rag.RagNotConnectedException;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * rag.enabled=true 로 켜도 RAG 서버 구현이 이 저장소에 없다 — 인터페이스 빈은 등록되지만 "미연결"이다.
 */
@IntegrationTest
@TestPropertySource(properties = "rag.enabled=true")
class RagEnabledNotConnectedTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RagClient ragClient;
    @Autowired
    JwtUtil jwtUtil;

    private Cookie login() {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtUtil.generateToken(1L));
    }

    private static String q(String text) {
        return "{\"question\":\"" + text + "\"}";
    }

    @Test
    void enablingRegistersTheInterfaceBeanAsNotConnected() {
        assertThat(ragClient).isInstanceOf(NotConnectedRagClient.class);
        assertThat(ragClient.isConnected()).isFalse();
        assertThatThrownBy(() -> ragClient.ask("질문")).isInstanceOf(RagNotConnectedException.class);
    }

    @Test
    void statusSaysEnabledButNotConnected() throws Exception {
        mvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void askingRequiresALogin() throws Exception {
        mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON).content(q("안녕")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void askingWhileNotConnectedIs503WithAnHonestDevelopmentMessage() throws Exception {
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON).content(q("안녕")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RAG_NOT_CONNECTED"))
                .andExpect(jsonPath("$.error").value(containsString("개발 중")))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void invalidQuestionsAreRejectedBeforeAnythingIsCalled() throws Exception {
        for (String body : new String[]{"", "{}", "{\"question\":\"\"}", "{\"question\":\"   \"}", "{\"question\":123}", "{\"question\":null}"}) {
            mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUESTION"));
        }
        // 길이 상한(500자): 500 은 통과(미연결 503), 501 은 거부
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON).content(q("가".repeat(500))))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON).content(q("가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("가가가"))));
    }
}
