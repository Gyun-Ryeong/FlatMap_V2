package com.flatmap.navigation.security;

import com.flatmap.navigation.config.AdminApiKeyFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** 키 설정 여부에 따른 필터 동작 (스프링 컨텍스트·DB 불필요). */
class AdminApiKeyFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticatedAsAdmin(AdminApiKeyFilter filter, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/data/fetch/all");
        if (headerValue != null) request.addHeader(AdminApiKeyFilter.HEADER, headerValue);
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void correctKeyGrantsAdmin() throws Exception {
        assertThat(authenticatedAsAdmin(new AdminApiKeyFilter("secret-key"), "secret-key")).isTrue();
    }

    @Test
    void wrongOrMissingKeyDoesNotGrantAdmin() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter("secret-key");
        assertThat(authenticatedAsAdmin(filter, "other")).isFalse();
        SecurityContextHolder.clearContext();
        assertThat(authenticatedAsAdmin(filter, "")).isFalse();
        SecurityContextHolder.clearContext();
        assertThat(authenticatedAsAdmin(filter, null)).isFalse();
    }

    @Test
    void unconfiguredKeyFailsClosed_evenForEmptyHeaderOrAnyValue() throws Exception {
        for (String configured : new String[]{"", "   ", null}) {
            AdminApiKeyFilter filter = new AdminApiKeyFilter(configured);
            assertThat(authenticatedAsAdmin(filter, "")).isFalse();
            SecurityContextHolder.clearContext();
            assertThat(authenticatedAsAdmin(filter, "anything")).isFalse();
            SecurityContextHolder.clearContext();
            assertThat(authenticatedAsAdmin(filter, configured)).isFalse();
            SecurityContextHolder.clearContext();
        }
    }
}
