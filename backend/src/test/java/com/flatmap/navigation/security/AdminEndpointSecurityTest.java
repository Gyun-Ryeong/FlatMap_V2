package com.flatmap.navigation.security;

import com.flatmap.navigation.config.AdminApiKeyFilter;
import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데이터 수집·갱신 계열 관리용 엔드포인트는 X-Admin-Api-Key 없이는 401/403 이어야 하고,
 * 더미 데이터 엔드포인트는 키가 있어도 존재하지 않아야 한다.
 */
@IntegrationTest
class AdminEndpointSecurityTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtUtil jwtUtil;

    @Value("${admin.api-key}")
    String adminApiKey;

    static Stream<Arguments> adminEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/all"),
                Arguments.of(HttpMethod.POST, "/api/safety/facilities/sync"),
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/accident-zones"),
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/protected-zones"),
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/welfare-facilities"),
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/shade-shelters"),
                Arguments.of(HttpMethod.POST, "/api/safety/fetch/senior-centers"),
                Arguments.of(HttpMethod.POST, "/api/data/fetch/all"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/fetch/all"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/fetch/gyeonggi"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/fetch/disaster"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/fetch/public-data"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/update-grade/elevation"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/update-grade/values"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/import/csv"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/import/seongnam-grades"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/sync"),
                Arguments.of(HttpMethod.POST, "/api/density-grid/recalculate"),
                Arguments.of(HttpMethod.POST, "/api/walk-difficulty/grid/rebuild"));
    }

    static Stream<Arguments> removedDummyEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/safety/seed/all"),
                Arguments.of(HttpMethod.POST, "/api/data/seed/all"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/seed"),
                Arguments.of(HttpMethod.POST, "/api/steep-slope/seed/seongnam"),
                Arguments.of(HttpMethod.DELETE, "/api/steep-slope/dummy"));
    }

    private MockHttpServletRequestBuilder req(HttpMethod method, String path) {
        return request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1} — 키 없음 → 401")
    @MethodSource("adminEndpoints")
    void withoutApiKeyIsUnauthorized(HttpMethod method, String path) throws Exception {
        mvc.perform(req(method, path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} — 틀린 키 → 401")
    @MethodSource("adminEndpoints")
    void withWrongApiKeyIsUnauthorized(HttpMethod method, String path) throws Exception {
        mvc.perform(req(method, path).header(AdminApiKeyFilter.HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} — 일반 로그인 사용자 → 403")
    @MethodSource("adminEndpoints")
    void loggedInNormalUserIsForbidden(HttpMethod method, String path) throws Exception {
        Cookie jwt = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtUtil.generateToken(1L));
        mvc.perform(req(method, path).cookie(jwt)).andExpect(status().isForbidden());
    }

    @Test
    void validApiKeyReachesTheController() throws Exception {
        // 빈 목록이면 DB 쓰기·외부 호출 없이 200 을 돌려준다.
        mvc.perform(request(HttpMethod.POST, "/api/steep-slope/update-grade/values")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }

    @ParameterizedTest(name = "{0} {1} — 키가 있어도 제거되어 404")
    @MethodSource("removedDummyEndpoints")
    void dummyEndpointsAreRemoved(HttpMethod method, String path) throws Exception {
        mvc.perform(req(method, path).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicReadEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/safety/cctv")).andExpect(status().isOk());
        mvc.perform(get("/api/safety/security-lights")).andExpect(status().isOk());
        mvc.perform(get("/api/safety/stats")).andExpect(status().isOk());
        mvc.perform(get("/api/steep-slope")).andExpect(status().isOk());
        mvc.perform(get("/api/steep-slope/stats")).andExpect(status().isOk());
        mvc.perform(get("/api/density-grid")).andExpect(status().isOk());
    }

    @Test
    void favoritesStillRequireLogin() throws Exception {
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
    }
}
