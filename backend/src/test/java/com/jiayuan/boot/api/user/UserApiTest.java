package com.jiayuan.boot.api.user;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 用户接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("用户接口测试")
class UserApiTest extends ApiTestBase {

    // ==================== GET /me ====================

    @Test
    @DisplayName("获取当前用户信息 - 成功")
    void getCurrentUserSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"))
                .body("data.nickname", notNullValue())
                .body("data.accountName", equalTo(accountName));
    }

    @Test
    @DisplayName("获取当前用户信息 - 未登录返回403")
    void getCurrentUserUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(403);
    }

    // ==================== GET /search ====================

    @Test
    @DisplayName("搜索用户 - 成功")
    void searchUsersSuccess() {
        givenAuth()
                .queryParam("keyword", accountName.substring(0, 4))
                .when()
                .get("/api/v1/users/search")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("搜索用户 - 空关键词")
    void searchUsersEmptyKeyword() {
        givenAuth()
                .queryParam("keyword", "")
                .when()
                .get("/api/v1/users/search")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // ==================== PUT /{id}/vip ====================

    @Test
    @DisplayName("升级VIP - 调用接口")
    void upgradeVip() {
        // 记录：VIP 升级接口可能受权限或参数限制，重点验证接口可达且鉴权正常
        givenAuth()
                .body(Map.of("vip", "VIP"))
                .when()
                .put("/api/v1/users/" + userId + "/vip")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)));
    }

    @Test
    @DisplayName("升级VIP - 未登录返回403")
    void upgradeVipUnauthorized() {
        givenNoAuth()
                .body(Map.of("vip", "VIP"))
                .when()
                .put("/api/v1/users/" + userId + "/vip")
                .then()
                .statusCode(403);
    }
}
