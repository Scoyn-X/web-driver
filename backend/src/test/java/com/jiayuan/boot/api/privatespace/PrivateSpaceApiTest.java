package com.jiayuan.boot.api.privatespace;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 私密空间接口 API 黑盒测试。
 * 私密空间需要 VIP 身份，且需要先设置密码。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("私密空间接口测试")
class PrivateSpaceApiTest extends ApiTestBase {

    // ==================== GET /api/v1/personal/private-space/status ====================

    @Test
    @DisplayName("获取私密空间状态 - 成功")
    void getStatusSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/personal/private-space/status")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取私密空间状态 - 未登录返回403")
    void getStatusUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/personal/private-space/status")
                .then()
                .statusCode(403);
    }

    // ==================== PUT /api/v1/personal/private-space/password ====================

    @Test
    @DisplayName("设置私密空间密码 - 未登录返回403")
    void setPasswordUnauthorized() {
        givenNoAuth()
                .body(Map.of("password", "test1234"))
                .when()
                .put("/api/v1/personal/private-space/password")
                .then()
                .statusCode(403);
    }

    // ==================== POST /api/v1/personal/private-space/session ====================

    @Test
    @DisplayName("解锁私密空间 - 未设置密码")
    void unlockWithoutPassword() {
        givenAuth()
                .body(Map.of("password", "test1234"))
                .when()
                .post("/api/v1/personal/private-space/session")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== GET /api/v1/personal/private-space/directories ====================

    @Test
    @DisplayName("获取私密空间目录 - 未解锁")
    void listPrivateDirectoriesWithoutUnlock() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/personal/private-space/directories")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取私密空间目录 - 未登录返回403")
    void listPrivateDirectoriesUnauthorized() {
        givenNoAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/personal/private-space/directories")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/personal/private-space/files ====================

    @Test
    @DisplayName("获取私密空间文件 - 未解锁")
    void listPrivateFilesWithoutUnlock() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/personal/private-space/files")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== GET /api/v1/personal/private-space/trash ====================

    @Test
    @DisplayName("获取私密空间回收站 - 未登录返回403")
    void listPrivateTrashUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/personal/private-space/trash")
                .then()
                .statusCode(403);
    }
}
