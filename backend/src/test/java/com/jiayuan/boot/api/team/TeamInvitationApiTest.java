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
 * 团队邀请接口 API 黑盒测试。
 * 需要创建团队和第二个用户才能完整测试邀请流程。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队邀请接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamInvitationApiTest extends ApiTestBase {

    private static Long teamId;

    @Test
    @Order(1)
    @DisplayName("创建团队作为前置条件")
    void createTeam() {
        Response resp = givenAuth()
                .body(Map.of("name", "邀请测试团队_" + System.currentTimeMillis(),
                        "description", "邀请测试"))
                .when()
                .post("/api/v1/team");
        assertSuccess(resp);
        teamId = resp.jsonPath().getLong("data.id");
    }

    // ==================== GET /api/v1/team/{teamId}/invitations ====================

    @Test
    @Order(2)
    @DisplayName("获取团队邀请列表 - 成功")
    void listInvitationsSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/" + teamId + "/invitations")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取团队邀请列表 - 未登录返回403")
    void listInvitationsUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/invitations")
                .then()
                .statusCode(403);
    }

    // ==================== POST /api/v1/team/{teamId}/invitations/actions ====================

    @Test
    @DisplayName("邀请操作 - 邀请不存在的用户")
    void inviteNonExistentUser() {
        givenAuth()
                .body(Map.of("action", "INVITE", "inviteeAccountId", 99999, "roleCode", "Editor"))
                .when()
                .post("/api/v1/team/" + teamId + "/invitations/actions")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("邀请操作 - 未登录返回403")
    void inviteUnauthorized() {
        givenNoAuth()
                .body(Map.of("action", "INVITE", "inviteeAccountId", 1, "roleCode", "Editor"))
                .when()
                .post("/api/v1/team/1/invitations/actions")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/users/me/team-invitations ====================

    @Test
    @DisplayName("获取我的邀请 - 成功")
    void listMyInvitationsSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/users/me/team-invitations")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    // ==================== GET /api/v1/team/invitations/received ====================

    @Test
    @DisplayName("获取收到的邀请 - 成功")
    void listReceivedInvitationsSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/team/invitations/received")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
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
