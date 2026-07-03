package com.jiayuan.boot.api.team;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 菜单接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("菜单接口测试")
class TeamMenuApiTest extends ApiTestBase {

    @Test
    @DisplayName("获取个人菜单 - 成功")
    void getMyMenusSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/me/menus")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取个人菜单 - 未登录返回403")
    void getMyMenusUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/me/menus")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("获取团队菜单 - 团队不存在")
    void getTeamMenusNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999/menus")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取团队菜单 - 未登录返回403")
    void getTeamMenusUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/menus")
                .then()
                .statusCode(403);
    }
}
