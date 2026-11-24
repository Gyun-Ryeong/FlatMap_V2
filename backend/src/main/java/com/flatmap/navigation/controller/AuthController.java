package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.JwtAuthenticationFilter;
import com.flatmap.navigation.repository.UserRepository;
import com.flatmap.navigation.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${kakao.oauth.redirect-uri}")
    private String kakaoRedirectUri;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin() {
        String authorizeUrl = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + kakaoRestApiKey
                + "&redirect_uri=" + kakaoRedirectUri
                + "&response_type=code";

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizeUrl))
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(@RequestParam("code") String code, HttpServletResponse response) {
        try {
            String jwt = authService.loginWithKakao(code);

            Cookie cookie = authCookie(jwt, (int) (expirationMs / 1000));
            response.addCookie(cookie);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/"))
                    .build();
        } catch (Exception e) {
            log.error("카카오 로그인 실패", e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/?loginError=1"))
                    .build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
        }

        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of(
                        "id", user.getId(),
                        "nickname", user.getNickname() == null ? "" : user.getNickname(),
                        "profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "사용자를 찾을 수 없습니다.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addCookie(authCookie("", 0));
        return ResponseEntity.noContent().build();
    }

    /** 로그인 쿠키: HttpOnly, SameSite=Lax, 운영(HTTPS)에서는 Secure. 발급과 삭제가 같은 속성을 쓴다. */
    private Cookie authCookie(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
