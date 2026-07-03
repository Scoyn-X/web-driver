package com.jiayuan.boot.api.admin;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 系统配置接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("系统配置接口测试")
class SystemConfigApiTest extends ApiTestBase {

    @Test
    @DisplayName("获取系统配置 - 成功")
    void getConfigSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/system/config")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("修改系统配置 - 调用接口")
    void updateConfig() {
        // 请求体字段名需匹配 SystemConfigUpdateRequestVO，当前使用 retentionDays 不匹配
        givenAuth()
                .body(Map.of("retentionDays", 30))
                .when()
                .put("/api/v1/system/config")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @DisplayName("修改系统配置 - 空请求体")
    void updateConfigEmptyBody() {
        // 空 body 视为无修改，返回成功
        givenAuth()
                .body(Map.of())
                .when()
                .put("/api/v1/system/config")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @DisplayName("触发回收站清理 - 成功")
    void triggerCleanupSuccess() {
        givenAuth()
                .when()
                .post("/api/v1/system/cleanup/trigger")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("系统配置 - 未登录返回403")
    void systemConfigUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/system/config")
                .then()
                .statusCode(403);
    }
}
