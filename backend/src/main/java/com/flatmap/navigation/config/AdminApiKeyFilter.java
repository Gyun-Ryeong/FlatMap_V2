package com.flatmap.navigation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 관리용 엔드포인트(데이터 수집·갱신)용 고정 API 키 인증.
 *
 * <p>{@code X-Admin-Api-Key} 헤더 값이 설정된 {@code admin.api-key} 와 일치하면 ROLE_ADMIN 으로 인증한다.
 * 키가 설정되지 않았으면(빈 값) 어떤 요청도 ADMIN 으로 인증하지 않는다(fail closed).
 * 어떤 경로가 ADMIN 을 요구하는지는 {@link SecurityConfig} 가 정한다.
 *
 * <p>JWT/사용자 모델(현재 사용자에게 역할이 없다)을 건드리지 않기 위해 역할 기반 대신 이 방식을 택했다.
 */
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Api-Key";

    private final byte[] expectedDigest;

    public AdminApiKeyFilter(@Value("${admin.api-key:}") String adminApiKey) {
        this.expectedDigest = (adminApiKey == null || adminApiKey.isBlank()) ? null : sha256(adminApiKey);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedDigest != null && provided != null && !provided.isEmpty()
                // 길이 정보가 새지 않도록 해시끼리 상수 시간 비교한다.
                && MessageDigest.isEqual(expectedDigest, sha256(provided))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin-api-key", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
