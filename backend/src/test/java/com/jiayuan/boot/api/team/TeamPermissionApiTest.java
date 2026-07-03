package com.jiayuan.boot.api.team;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 团队权限和角色接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队权限接口测试")
class TeamPermissionApiTest extends ApiTestBase {

    // ==================== GET /api/v1/permissions ====================

    @Test
    @DisplayName("获取系统权限点列表 - 成功")
    void listPermissionsSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/permissions")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取系统权限点列表 - 未登录返回403")
    void listPermissionsUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/permissions")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/roles ====================

    @Test
    @DisplayName("获取系统角色列表 - 成功")
    void listRolesSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/roles")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取系统角色列表 - 未登录返回403")
    void listRolesUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/roles")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/team/{teamId}/permissions ====================

    @Test
    @DisplayName("获取团队权限 - 团队不存在")
    void getTeamPermissionsNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999/permissions")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取团队权限 - 未登录返回403")
    void getTeamPermissionsUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/permissions")
                .then()
                .statusCode(403);
    }
}
