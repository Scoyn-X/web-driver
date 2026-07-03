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
 * 团队成员接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队成员接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberApiTest extends ApiTestBase {

    private static Long teamId;

    @Test
    @Order(1)
    @DisplayName("创建团队作为前置条件")
    void createTeam() {
        Response resp = givenAuth()
                .body(Map.of("name", "成员测试团队_" + System.currentTimeMillis(),
                        "description", "成员测试"))
                .when()
                .post("/api/v1/team");
        assertSuccess(resp);
        teamId = resp.jsonPath().getLong("data.id");
    }

    // ==================== GET /{teamId}/members ====================

    @Test
    @Order(2)
    @DisplayName("列出团队成员 - 成功")
    void listMembersSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/members")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("列出团队成员 - 团队不存在")
    void listMembersNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999/members")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("列出团队成员 - 未登录返回403")
    void listMembersUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/members")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /{teamId}/members/{memberId} ====================

    @Test
    @DisplayName("移除成员 - 不存在的成员")
    void removeMissingMember() {
        givenAuth()
                .when()
                .delete("/api/v1/team/" + teamId + "/members/99999")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("移除成员 - 未登录返回403")
    void removeMemberUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/team/1/members/1")
                .then()
                .statusCode(403);
    }

    // ==================== PUT /{teamId}/members/{memberId}/role ====================

    @Test
    @DisplayName("修改成员角色 - 不存在的成员")
    void updateRoleMissingMember() {
        givenAuth()
                .body(Map.of("role", "Editor"))
                .when()
                .put("/api/v1/team/" + teamId + "/members/99999/role")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    // ==================== PUT /{teamId}/owner/transfer ====================

    @Test
    @DisplayName("转让所有权 - 目标不存在")
    void transferOwnerInvalidTarget() {
        givenAuth()
                .body(Map.of("targetUserId", 99999))
                .when()
                .put("/api/v1/team/" + teamId + "/owner/transfer")
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
