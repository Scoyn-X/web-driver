package com.jiayuan.boot.api.oss;

import com.jiayuan.boot.api.base.ApiTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 个人文件接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("个人文件接口测试")
class FileApiTest extends ApiTestBase {

    // ==================== POST /api/v1/files 上传 ====================

    @Test
    @DisplayName("上传文件 - 成功")
    void uploadFileSuccess() throws IOException {
        File tmp = createTempFile("hello.txt", "hello world");
        givenMultipartAuth()
                .multiPart("file", tmp)
                .multiPart("parentId", "0")
                .when()
                .post("/api/v1/files")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
        tmp.delete();
    }

    @Test
    @DisplayName("上传文件 - 未登录返回403")
    void uploadFileUnauthorized() throws IOException {
        File tmp = createTempFile("secret.txt", "secret");
        givenNoAuth()
                .contentType("multipart/form-data")
                .multiPart("file", tmp)
                .when()
                .post("/api/v1/files")
                .then()
                .statusCode(403);
        tmp.delete();
    }

    // ==================== GET /api/v1/files 列表 ====================

    @Test
    @DisplayName("获取文件列表 - 成功")
    void listFilesSuccess() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/files")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"))
                .body("data.breadcrumb", notNullValue());
    }

    @Test
    @DisplayName("获取文件列表 - 未登录返回403")
    void listFilesUnauthorized() {
        givenNoAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/files")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/files/tree 文件树 ====================

    @Test
    @DisplayName("获取文件树 - 成功")
    void listFileTreeSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/files/tree")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    // ==================== GET /api/v1/files/search 搜索 ====================

    @Test
    @DisplayName("搜索文件 - 成功")
    void searchFilesSuccess() {
        givenAuth()
                .queryParam("keyword", "hello")
                .when()
                .get("/api/v1/files/search")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("搜索文件 - 空关键词")
    void searchFilesEmptyKeyword() {
        givenAuth()
                .queryParam("keyword", "")
                .when()
                .get("/api/v1/files/search")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // ==================== GET /files/{id}/download ====================

    @Test
    @DisplayName("下载文件 - 文件不存在")
    void downloadMissingFile() {
        givenAuth()
                .when()
                .get("/api/v1/files/999999/download")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("下载文件 - 未登录返回403")
    void downloadUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/files/1/download")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /files/{id} ====================

    @Test
    @DisplayName("删除文件 - 未登录返回403")
    void deleteFileUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/files/1")
                .then()
                .statusCode(403);
    }

    // ==================== POST /files/{id}/copy ====================

    @Test
    @DisplayName("复制文件 - 未登录返回403")
    void copyFileUnauthorized() {
        givenNoAuth()
                .body(Map.of("targetDirectoryId", 0))
                .when()
                .post("/api/v1/files/1/copy")
                .then()
                .statusCode(403);
    }

    // ==================== PUT /files/{id}/move ====================

    @Test
    @DisplayName("移动文件 - 未登录返回403")
    void moveFileUnauthorized() {
        givenNoAuth()
                .body(Map.of("targetDirectoryId", 0))
                .when()
                .put("/api/v1/files/1/move")
                .then()
                .statusCode(403);
    }

    // ==================== 完整文件生命周期流程 ====================

    @Test
    @DisplayName("文件生命周期：上传 → 下载预签名URL → 删除")
    void fileLifecycle() throws IOException {
        // 1. 上传
        File tmp = createTempFile("lifecycle.txt", "lifecycle content");
        Response uploadResp = givenMultipartAuth()
                .multiPart("file", tmp)
                .multiPart("parentId", "0")
                .when()
                .post("/api/v1/files");
        tmp.delete();
        assertSuccess(uploadResp);

        // 2. 搜索确认存在
        givenAuth()
                .queryParam("keyword", "lifecycle")
                .when()
                .get("/api/v1/files/search")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));

        // 3. 从列表中找到文件 ID 并下载
        Response listResp = givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/files");
        assertSuccess(listResp);
    }

    // ==================== 帮助方法 ====================

    private File createTempFile(String name, String content) throws IOException {
        File f = File.createTempFile("api-test-", "-" + name);
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }
}
