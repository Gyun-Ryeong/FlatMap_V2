package com.flatmap.navigation.web;

import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.rag.RagClient;
import com.flatmap.navigation.rag.RagUnavailableException;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 연동 지점이 실제로 "붙일 수 있는" 모양인지 확인한다: 가짜 구현체를 {@code @Primary} 로 등록하면 기본 미연결 빈 대신 쓰인다.
 * 이 가짜 구현체는 테스트 전용이다 — 저장소에는 RAG 서버 구현이 없다.
 */
@IntegrationTest
@TestPropertySource(properties = "rag.enabled=true")
@Import(RagPluggedInClientTest.FakeRag.class)
class RagPluggedInClientTest {

    /** 테스트 전용 가짜 RAG: "실패" 가 들어간 질문이면 내부 정보가 든 메시지로 실패한다. */
    @TestConfiguration
    static class FakeRag {
        @Bean
        @Primary
        RagClient fakeRagClient() {
            return new RagClient() {
                @Override
                public boolean isConnected() {
                    return true;
                }

                @Override
                public RagAnswer ask(String question) {
                    if (question.contains("실패")) {
                        throw new RagUnavailableException("GET https://rag.internal/?apiKey=SHOULD-NOT-LEAK 500");
                    }
                    if (question.contains("폭발")) {
                        throw new IllegalStateException("db password=SHOULD-NOT-LEAK");
                    }
                    return new RagAnswer("테스트 답변: " + question, List.of("출처 A", "출처 B"));
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    RagClient ragClient;
    @Autowired
    JwtUtil jwtUtil;

    private Cookie login() {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtUtil.generateToken(1L));
    }

    @Test
    void aPluggedInImplementationReplacesTheNotConnectedDefault() throws Exception {
        assertThat(ragClient.isConnected()).isTrue();
        mvc.perform(get("/api/rag/status"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connected").value(true));
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  안녕  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("테스트 답변: 안녕"))     // 앞뒤 공백은 정리해서 넘긴다
                .andExpect(jsonPath("$.sources[0]").value("출처 A"))
                .andExpect(jsonPath("$.sources.length()").value(2));
    }

    @Test
    void implementationFailuresAreGeneralizedAndNeverLeakInternals() throws Exception {
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"실패해라\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RAG_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("SHOULD-NOT-LEAK"))))
                .andExpect(content().string(not(containsString("rag.internal"))));
        // 예상 밖 예외도 일반화된 500 이다
        mvc.perform(post("/api/rag/ask").cookie(login()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"폭발\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("SHOULD-NOT-LEAK"))));
    }
}
