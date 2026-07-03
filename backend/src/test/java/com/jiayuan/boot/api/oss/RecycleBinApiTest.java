package com.jiayuan.boot.api.oss;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 个人回收站接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("个人回收站接口测试")
class RecycleBinApiTest extends ApiTestBase {

    // ==================== GET /api/v1/recycle-bin ====================

    @Test
    @DisplayName("获取回收站列表 - 成功")
    void listRecycleBinSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/recycle-bin")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取回收站列表 - 未登录返回403")
    void listRecycleBinUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/recycle-bin")
                .then()
                .statusCode(403);
    }

    // ==================== POST /api/v1/recycle-bin/{id}/restore ====================

    @Test
    @DisplayName("恢复回收站文件 - 不存在的记录")
    void restoreMissingItem() {
        givenAuth()
                .when()
                .post("/api/v1/recycle-bin/999999/restore")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("恢复回收站文件 - 未登录返回403")
    void restoreUnauthorized() {
        givenNoAuth()
                .when()
                .post("/api/v1/recycle-bin/1/restore")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /api/v1/recycle-bin/{id} ====================

    @Test
    @DisplayName("彻底删除回收站文件 - 不存在的记录")
    void permanentDeleteMissingItem() {
        givenAuth()
                .when()
                .delete("/api/v1/recycle-bin/999999")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("彻底删除回收站文件 - 未登录返回403")
    void permanentDeleteUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/recycle-bin/1")
                .then()
                .statusCode(403);
    }
}
