package com.jiayuan.boot.system.security.util;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 请求上下文工具测试。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@DisplayName("JwtTokenUtils 单元测试")
class JwtTokenUtilsTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("当前请求存在 Bearer token 时返回 token 哈希")
    void getCurrentTokenHash_success() {
        String token = "jwt-token-a";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String tokenHash = JwtTokenUtils.getCurrentTokenHash();

        assertThat(tokenHash).isEqualTo(DigestUtil.sha256Hex(token));
    }

    @Test
    @DisplayName("当前请求缺少 Bearer token 时拒绝")
    void getCurrentBearerToken_missingToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(JwtTokenUtils::getCurrentBearerToken)
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_TOKEN_INVALID);
    }
}
