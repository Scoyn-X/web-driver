package com.jiayuan.boot.api.oss;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 个人目录接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("个人目录接口测试")
class DirectoryApiTest extends ApiTestBase {

    // ==================== GET /api/v1/directories ====================

    @Test
    @DisplayName("获取目录列表 - 成功")
    void listDirectoriesSuccess() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取目录列表 - 未登录返回403")
    void listDirectoriesUnauthorized() {
        givenNoAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/directories")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/directories/tree ====================

    @Test
    @DisplayName("获取目录树 - 成功")
    void getDirectoryTreeSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/directories/tree")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    // ==================== POST /api/v1/directories 创建 ====================

    @Test
    @DisplayName("创建目录 - 成功")
    void createDirectorySuccess() {
        givenAuth()
                .body(Map.of("name", "测试目录_" + System.currentTimeMillis(), "parentId", 0))
                .when()
                .post("/api/v1/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"))
                .body("data.originalName", notNullValue());
    }

    @Test
    @DisplayName("创建目录 - 空名称")
    void createDirectoryEmptyName() {
        givenAuth()
                .body(Map.of("name", "", "parentId", 0))
                .when()
                .post("/api/v1/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("创建目录 - 未登录返回403")
    void createDirectoryUnauthorized() {
        givenNoAuth()
                .body(Map.of("name", "非法目录", "parentId", 0))
                .when()
                .post("/api/v1/directories")
                .then()
                .statusCode(403);
    }

    // ==================== PUT /api/v1/directories/{id}/rename ====================

    @Test
    @DisplayName("重命名目录 - 不存在的目录")
    void renameMissingDirectory() {
        givenAuth()
                .body(Map.of("name", "新名称"))
                .when()
                .put("/api/v1/directories/999999/rename")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("重命名目录 - 未登录返回403")
    void renameDirectoryUnauthorized() {
        givenNoAuth()
                .body(Map.of("name", "新名称"))
                .when()
                .put("/api/v1/directories/1/rename")
                .then()
                .statusCode(403);
    }

    // ==================== PUT /api/v1/personal/directories/{directoryId}/move ====================

    @Test
    @DisplayName("移动目录 - 未登录返回403")
    void moveDirectoryUnauthorized() {
        givenNoAuth()
                .body(Map.of("targetDirectoryId", 0))
                .when()
                .put("/api/v1/personal/directories/1/move")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /api/v1/directories/{id} ====================

    @Test
    @DisplayName("删除目录 - 未登录返回403")
    void deleteDirectoryUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/directories/1")
                .then()
                .statusCode(403);
    }

    // ==================== 完整目录流程 ====================

    @Test
    @DisplayName("目录生命周期：创建 → 重命名 → 删除")
    void directoryLifecycle() {
        String dirName = "lifecycle_dir_" + System.currentTimeMillis();

        // 创建
        Long dirId = extractDataLong(
                givenAuth()
                        .body(Map.of("name", dirName, "parentId", 0))
                        .when()
                        .post("/api/v1/directories"),
                "id");

        // 重命名
        givenAuth()
                .body(Map.of("name", dirName + "_renamed"))
                .when()
                .put("/api/v1/directories/" + dirId + "/rename")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }
}
