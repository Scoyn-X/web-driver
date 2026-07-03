package com.jiayuan.boot.system.security.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 工具单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("JwtUtils 单元测试")
class JwtUtilsTest {

    @Test
    @DisplayName("生成并解析 Token：可取回用户、账户和用户名")
    void generateAndParseToken_roundTripsClaims() {
        JwtUtils jwtUtils = configuredJwtUtils();

        String token = jwtUtils.generateToken(7L, 70L, "alice");
        Claims claims = jwtUtils.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo(7L);
        assertThat(jwtUtils.getAccountIdFromToken(token)).isEqualTo(70L);
        assertThat(jwtUtils.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("解析非法 Token：抛出 JWT 解析异常")
    void parseToken_invalidTokenThrows() {
        JwtUtils jwtUtils = configuredJwtUtils();

        assertThatThrownBy(() -> jwtUtils.parseToken("invalid.token.value"))
                .isInstanceOf(RuntimeException.class);
    }

    private static JwtUtils configuredJwtUtils() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtils, "expiration", 60000L);
        return jwtUtils;
    }
}
