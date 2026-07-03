package com.jiayuan.boot.api.share;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 分享访问接口（匿名） API 黑盒测试。
 * 这些端点位于 /api/v1/s/** ，无需登录。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("分享匿名访问接口测试")
class ShareAccessApiTest extends ApiTestBase {

    private static final String INVALID_TOKEN = "invalid_token_1234567890123456";

    // ==================== GET /s/{shareToken} ====================

    @Test
    @DisplayName("访问分享链接 - 无效 token")
    void accessInvalidToken() {
        givenNoAuth()
                .when()
                .get("/api/v1/s/" + INVALID_TOKEN)
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("访问分享链接 - 空 token")
    void accessEmptyToken() {
        givenNoAuth()
                .when()
                .get("/api/v1/s/ ")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== POST /s/{shareToken}/verify ====================

    @Test
    @DisplayName("验证提取码 - 无效 token")
    void verifyInvalidToken() {
        givenNoAuth()
                .body(java.util.Map.of("extractCode", "ABCD"))
                .when()
                .post("/api/v1/s/" + INVALID_TOKEN + "/verify")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== GET /s/{shareToken}/download ====================

    @Test
    @DisplayName("下载分享文件 - 无效 token")
    void downloadInvalidToken() {
        givenNoAuth()
                .when()
                .get("/api/v1/s/" + INVALID_TOKEN + "/download")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }
}
