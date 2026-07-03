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
 * 团队生命周期接口 API 黑盒测试。
 * 测试方法按顺序执行以复用创建的团队。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队生命周期接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamApiTest extends ApiTestBase {

    private static Long teamId;

    // ==================== POST /api/v1/team 创建 ====================

    @Test
    @Order(1)
    @DisplayName("创建团队 - 成功")
    void createTeamSuccess() {
        Response resp = givenAuth()
                .body(Map.of("name", "测试团队_" + System.currentTimeMillis(),
                        "description", "API 测试用团队"))
                .when()
                .post("/api/v1/team");

        assertSuccess(resp);
        teamId = resp.jsonPath().getLong("data.id");
    }

    @Test
    @DisplayName("创建团队 - 空名称")
    void createTeamEmptyName() {
        givenAuth()
                .body(Map.of("name", ""))
                .when()
                .post("/api/v1/team")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("创建团队 - 未登录返回403")
    void createTeamUnauthorized() {
        givenNoAuth()
                .body(Map.of("name", "非法团队"))
                .when()
                .post("/api/v1/team")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/team 列表 ====================

    @Test
    @DisplayName("获取我的团队列表 - 成功")
    void listMyTeamsSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取我的团队列表 - 未登录返回403")
    void listMyTeamsUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/team/{teamId} ====================

    @Test
    @Order(2)
    @DisplayName("获取团队详情 - 成功")
    void getTeamDetailSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId)
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"))
                .body("data.name", notNullValue());
    }

    @Test
    @DisplayName("获取团队详情 - 团队不存在")
    void getTeamDetailNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== PUT /api/v1/team/{teamId} ====================

    @Test
    @Order(3)
    @DisplayName("更新团队信息 - 成功")
    void updateTeamSuccess() {
        givenAuth()
                .body(Map.of("name", "测试团队_已更新"))
                .when()
                .put("/api/v1/team/" + teamId)
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    // ==================== GET /api/v1/team/{teamId}/quota ====================

    @Test
    @Order(4)
    @DisplayName("获取团队配额 - 成功")
    void getTeamQuotaSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/quota")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"))
                .body("data.totalQuota", notNullValue())
                .body("data.usedSpace", notNullValue());
    }

    // ==================== POST /api/v1/team/{teamId}/dissolve ====================

    @Test
    @Order(5)
    @DisplayName("解散团队 - 成功")
    void dissolveTeamSuccess() {
        givenAuth()
                .body(Map.of("reason", "API 测试解散"))
                .when()
                .post("/api/v1/team/" + teamId + "/dissolve")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("解散团队 - 团队不存在")
    void dissolveTeamNotFound() {
        givenAuth()
                .when()
                .post("/api/v1/team/99999/dissolve")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }
}
