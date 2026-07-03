package com.jiayuan.boot.api.team;

import com.jiayuan.boot.api.base.ApiTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 团队回收站接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队回收站接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamTrashApiTest extends ApiTestBase {

    private static Long teamId;

    @Test
    @Order(1)
    @DisplayName("创建团队作为前置条件")
    void createTeam() {
        Response resp = givenAuth()
                .body(Map.of("name", "回收站测试团队_" + System.currentTimeMillis(),
                        "description", "回收站测试"))
                .when()
                .post("/api/v1/team");
        assertSuccess(resp);
        teamId = resp.jsonPath().getLong("data.id");
    }

    // ==================== GET /team/{teamId}/trash ====================

    @Test
    @Order(2)
    @DisplayName("获取团队回收站列表 - 成功")
    void listTrashSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/trash")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取团队回收站列表 - 团队不存在")
    void listTrashNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999/trash")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取团队回收站列表 - 未登录返回403")
    void listTrashUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/trash")
                .then()
                .statusCode(403);
    }

    // ==================== POST /team/{teamId}/trash/{trashId}/restore ====================

    @Test
    @DisplayName("恢复团队回收站文件 - 不存在的记录")
    void restoreMissingItem() {
        givenAuth()
                .when()
                .post("/api/v1/team/" + teamId + "/trash/999999/restore")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== DELETE /team/{teamId}/trash/{trashId} ====================

    @Test
    @DisplayName("彻底删除团队回收站文件 - 不存在的记录")
    void permanentDeleteMissingItem() {
        givenAuth()
                .when()
                .delete("/api/v1/team/" + teamId + "/trash/999999")
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
}
