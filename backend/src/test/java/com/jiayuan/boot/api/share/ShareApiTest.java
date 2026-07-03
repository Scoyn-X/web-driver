package com.jiayuan.boot.api.share;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 个人分享接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("个人分享接口测试")
class ShareApiTest extends ApiTestBase {

    // ==================== POST /api/v1/shares ====================

    @Test
    @DisplayName("创建分享 - 文件不存在")
    void createShareFileNotFound() {
        givenAuth()
                .body(Map.of("fileId", 999999, "accessType", 0, "expireDays", 7))
                .when()
                .post("/api/v1/shares")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("创建分享 - 参数缺失")
    void createShareMissingParams() {
        givenAuth()
                .body(Map.of("accessType", 0))
                .when()
                .post("/api/v1/shares")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("创建分享 - 未登录返回403")
    void createShareUnauthorized() {
        givenNoAuth()
                .body(Map.of("fileId", 1, "accessType", 0))
                .when()
                .post("/api/v1/shares")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/shares ====================

    @Test
    @DisplayName("获取分享列表 - 成功")
    void listSharesSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/shares")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取分享列表 - 未登录返回403")
    void listSharesUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/shares")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /api/v1/shares/{id} ====================

    @Test
    @DisplayName("取消分享 - 不存在的分享")
    void cancelMissingShare() {
        givenAuth()
                .when()
                .delete("/api/v1/shares/999999")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("取消分享 - 未登录返回403")
    void cancelShareUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/shares/1")
                .then()
                .statusCode(403);
    }
}
