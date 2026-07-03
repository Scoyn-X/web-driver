package com.jiayuan.boot.system.security.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SecurityUtils 单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("SecurityUtils 单元测试")
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("当前认证信息存在时返回用户、账户和用户名")
    void currentAuthentication_returnsUserAccountAndUsername() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(7L);
        when(authentication.getDetails()).thenReturn(70L);
        when(authentication.getCredentials()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(7L);
        assertThat(SecurityUtils.getCurrentAccountId()).isEqualTo(70L);
        assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("当前认证信息缺失时抛异常")
    void missingAuthenticationThrowsRuntimeException() {
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("未获取到当前登录用户信息");
    }
}
