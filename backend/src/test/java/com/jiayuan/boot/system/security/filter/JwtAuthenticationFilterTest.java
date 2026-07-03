package com.jiayuan.boot.system.security.filter;

import com.jiayuan.boot.system.security.util.JwtUtils;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 鉴权过滤器测试。
 *
 * @author charleslam
 * @since 2026/05/23
 */
@DisplayName("JwtAuthenticationFilter 单元测试")
class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("解析 Token 后保存当前用户与当前账户")
    void doFilterInternal_storesUserAndAccountIdentity() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer token-a");

        when(jwtUtils.getUserIdFromToken("token-a")).thenReturn(10L);
        when(jwtUtils.getAccountIdFromToken("token-a")).thenReturn(200L);
        when(jwtUtils.getUsernameFromToken("token-a")).thenReturn("account-a");

        filter.doFilter(request, response, chain);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(10L);
        assertThat(SecurityUtils.getCurrentAccountId()).isEqualTo(200L);
        assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("account-a");
        verify(chain).doFilter(request, response);
    }
}
