package com.jiayuan.boot.api.team;

import com.jiayuan.boot.api.base.ApiTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 团队文件接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队文件接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamFileApiTest extends ApiTestBase {

    private static Long teamId;

    @Test
    @Order(1)
    @DisplayName("创建团队作为前置条件")
    void createTeam() {
        Response resp = givenAuth()
                .body(Map.of("name", "文件测试团队_" + System.currentTimeMillis(),
                        "description", "文件测试"))
                .when()
                .post("/api/v1/team");
        assertSuccess(resp);
        teamId = resp.jsonPath().getLong("data.id");
    }

    // ==================== GET /team/{teamId}/directories ====================

    @Test
    @DisplayName("获取团队目录列表 - 团队不存在")
    void listDirectoriesNotFound() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/team/99999/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取团队目录列表 - 未登录返回403")
    void listDirectoriesUnauthorized() {
        givenNoAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/team/1/directories")
                .then()
                .statusCode(403);
    }

    // ==================== POST /team/{teamId}/directories ====================

    @Test
    @Order(2)
    @DisplayName("创建团队目录 - 成功")
    void createDirectorySuccess() {
        givenAuth()
                .body(Map.of("name", "团队目录_" + System.currentTimeMillis(), "parentId", 0))
                .when()
                .post("/api/v1/team/" + teamId + "/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("创建团队目录 - 空名称")
    void createDirectoryEmptyName() {
        givenAuth()
                .body(Map.of("name", "", "parentId", 0))
                .when()
                .post("/api/v1/team/" + teamId + "/directories")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== GET /team/{teamId}/files ====================

    @Test
    @Order(3)
    @DisplayName("获取团队文件列表 - 成功")
    void listFilesSuccess() {
        givenAuth()
                .queryParam("parentId", 0)
                .when()
                .get("/api/v1/team/" + teamId + "/files")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"))
                .body("data.breadcrumb", notNullValue());
    }

    // ==================== POST /team/{teamId}/files ====================

    @Test
    @Order(4)
    @DisplayName("上传团队文件 - 成功")
    void uploadFileSuccess() throws Exception {
        File tmp = createTempFile("team-file.txt", "team file content");
        givenMultipartAuth()
                .multiPart("file", tmp)
                .multiPart("parentId", "0")
                .when()
                .post("/api/v1/team/" + teamId + "/files")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
        tmp.delete();
    }

    // ==================== GET /team/{teamId}/files/search ====================

    @Test
    @DisplayName("搜索团队文件 - 空关键词")
    void searchFilesEmptyKeyword() {
        givenAuth()
                .queryParam("keyword", "")
                .when()
                .get("/api/v1/team/" + teamId + "/files/search")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // ==================== GET /team/{teamId}/files/tree ====================

    @Test
    @DisplayName("获取团队文件树 - 成功")
    void listFileTreeSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/files/tree")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    // ==================== POST /team/{teamId}/files/from-personal ====================

    @Test
    @DisplayName("转存个人文件到团队 - 文件不存在")
    void transferFromPersonalFileNotFound() {
        givenAuth()
                .body(Map.of("sourceFileId", 999999, "targetDirectoryId", 0))
                .when()
                .post("/api/v1/team/" + teamId + "/files/from-personal")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== GET /team/{teamId}/files/{fileId}/download ====================

    @Test
    @DisplayName("下载团队文件 - 文件不存在")
    void downloadMissingFile() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/files/999999/download")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== DELETE /team/{teamId}/files/{fileId} ====================

    @Test
    @DisplayName("删除团队文件 - 文件不存在")
    void deleteMissingFile() {
        givenAuth()
                .when()
                .delete("/api/v1/team/" + teamId + "/files/999999")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== 清理 ====================

    @Test
    @Order(Integer.MAX_VALUE)
    @DisplayName("解散测试团队")
    void dissolveTeam() {
        givenAuth()
                .when()
                .post("/api/v1/team/" + teamId + "/dissolve")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    private File createTempFile(String name, String content) throws Exception {
        File f = File.createTempFile("api-test-", "-" + name);
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }
}
