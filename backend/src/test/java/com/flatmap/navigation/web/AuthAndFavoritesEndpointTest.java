package com.flatmap.navigation.web;

import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.service.AuthService;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 쿠키(JWT) 인증과 즐겨찾기. 카카오 호출(AuthService)은 모킹하고 DB 는 실제 PostGIS 를 쓴다.
 * 핵심은 사용자 격리다: 다른 사용자의 즐겨찾기를 보거나 지울 수 없어야 한다.
 */
@IntegrationTest
class AuthAndFavoritesEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtUtil jwtUtil;

    @MockBean
    AuthService authService;

    long alice;
    long bob;

    @BeforeEach
    void setUp() {
        cleanUp();
        alice = insertUser("alice-kakao-id", "앨리스");
        bob = insertUser("bob-kakao-id", "밥");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM favorites");
        jdbc.update("DELETE FROM users");
    }

    private long insertUser(String providerId, String nickname) {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_id, nickname) VALUES ('KAKAO', ?, ?) RETURNING id",
                Long.class, providerId, nickname);
    }

    private Cookie login(long userId) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtUtil.generateToken(userId));
    }

    private static final String FAVORITE = """
            {"name":"집","address":"성남시 분당구","latitude":37.38,"longitude":127.12}""";

    private long addFavorite(Cookie as, String body) throws Exception {
        String res = mvc.perform(post("/api/favorites").cookie(as).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(res).read("$.id", Long.class);
    }

    // ---- 인증 ----

    @Test
    void favoritesRequireALogin() throws Exception {
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/favorites").contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/favorites/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTamperedOrForeignCookieIsNotALogin() throws Exception {
        String good = jwtUtil.generateToken(alice);
        Cookie tampered = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, good.substring(0, good.length() - 2) + "xx");
        Cookie garbage = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-jwt");
        Cookie wrongName = new Cookie("other_cookie", good);

        mvc.perform(get("/api/favorites").cookie(tampered)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/favorites").cookie(garbage)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/favorites").cookie(wrongName)).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheLoggedInUserAndNothingSensitive() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(login(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice))
                .andExpect(jsonPath("$.nickname").value("앨리스"))
                .andExpect(jsonPath("$.providerId").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        // 토큰은 유효하지만 사용자가 사라진 경우
        mvc.perform(get("/api/auth/me").cookie(login(9_999_999L))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutExpiresTheHttpOnlyCookie() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(JwtAuthenticationFilter.COOKIE_NAME, 0))
                .andExpect(cookie().httpOnly(JwtAuthenticationFilter.COOKIE_NAME, true))
                .andExpect(cookie().value(JwtAuthenticationFilter.COOKIE_NAME, ""));
    }

    // ---- 카카오 로그인 흐름 ----

    @Test
    void kakaoLoginRedirectsToKakaoAuthorize() throws Exception {
        mvc.perform(get("/api/auth/kakao/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/oauth/authorize?client_id=")))
                .andExpect(header().string("Location", containsString("response_type=code")));
    }

    @Test
    void kakaoCallbackSetsAnHttpOnlyCookieAndGoesBackToTheFrontend() throws Exception {
        Mockito.when(authService.loginWithKakao("good-code")).thenReturn(jwtUtil.generateToken(alice));

        mvc.perform(get("/api/auth/kakao/callback").param("code", "good-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/"))
                .andExpect(cookie().httpOnly(JwtAuthenticationFilter.COOKIE_NAME, true))
                .andExpect(cookie().path(JwtAuthenticationFilter.COOKIE_NAME, "/"));
    }

    @Test
    void kakaoCallbackFailureRedirectsWithAnErrorFlagAndLeaksNothing() throws Exception {
        Mockito.when(authService.loginWithKakao("bad-code"))
                .thenThrow(new RuntimeException("kauth.kakao.com 응답: client_secret=SHOULD-NOT-LEAK"));

        mvc.perform(get("/api/auth/kakao/callback").param("code", "bad-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/?loginError=1"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(not(containsString("SHOULD-NOT-LEAK"))));
    }

    // ---- 즐겨찾기: 기본 동작 ----

    @Test
    void addListAndDeleteOwnFavorites() throws Exception {
        mvc.perform(post("/api/favorites").cookie(login(alice)).contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("집"))
                .andExpect(jsonPath("$.latitude").value(37.38));

        mvc.perform(get("/api/favorites").cookie(login(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].address").value("성남시 분당구"));

        long id = jdbc.queryForObject("SELECT id FROM favorites WHERE user_id = ?", Long.class, alice);
        mvc.perform(delete("/api/favorites/" + id).cookie(login(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        mvc.perform(get("/api/favorites").cookie(login(alice))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theSameCoordinateIsNotSavedTwice() throws Exception {
        mvc.perform(post("/api/favorites").cookie(login(alice)).contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favorites").cookie(login(alice)).contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isOk());          // 기존 것을 그대로 돌려준다
        mvc.perform(get("/api/favorites").cookie(login(alice))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void newestFavoriteComesFirst() throws Exception {
        addFavorite(login(alice), "{\"name\":\"먼저\",\"latitude\":37.1,\"longitude\":127.1}");
        Thread.sleep(15);
        addFavorite(login(alice), "{\"name\":\"나중\",\"latitude\":37.2,\"longitude\":127.2}");

        mvc.perform(get("/api/favorites").cookie(login(alice)))
                .andExpect(jsonPath("$[0].name").value("나중"))
                .andExpect(jsonPath("$[1].name").value("먼저"));
    }

    // ---- 즐겨찾기: 사용자 격리 ----

    @Test
    void usersOnlySeeTheirOwnFavorites() throws Exception {
        addFavorite(login(alice), FAVORITE);
        addFavorite(login(bob), "{\"name\":\"밥의 회사\",\"latitude\":37.5,\"longitude\":127.1}");

        mvc.perform(get("/api/favorites").cookie(login(alice)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("집"));
        mvc.perform(get("/api/favorites").cookie(login(bob)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("밥의 회사"));
    }

    @Test
    void aUserCannotDeleteSomeoneElsesFavorite() throws Exception {
        long bobsFavorite = addFavorite(login(bob), "{\"name\":\"밥의 회사\",\"latitude\":37.5,\"longitude\":127.1}");

        mvc.perform(delete("/api/favorites/" + bobsFavorite).cookie(login(alice)))
                .andExpect(status().isNotFound());                       // 존재 여부도 알려 주지 않는다
        // 지워지지 않았다
        mvc.perform(get("/api/favorites").cookie(login(bob))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sameCoordinateByDifferentUsersAreSeparateFavorites() throws Exception {
        mvc.perform(post("/api/favorites").cookie(login(alice)).contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isCreated());
        // 같은 좌표라도 다른 사용자에게는 중복이 아니다
        mvc.perform(post("/api/favorites").cookie(login(bob)).contentType(MediaType.APPLICATION_JSON).content(FAVORITE))
                .andExpect(status().isCreated());
    }

    @Test
    void deletingAUserRemovesTheirFavorites() {
        jdbc.update("INSERT INTO favorites (user_id, name, latitude, longitude) VALUES (?, 'x', 37.1, 127.1)", alice);
        jdbc.update("DELETE FROM users WHERE id = ?", alice);
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM favorites", Integer.class)).isZero();   // ON DELETE CASCADE
    }
}
