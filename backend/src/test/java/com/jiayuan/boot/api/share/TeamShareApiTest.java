package com.jiayuan.boot.api.share;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 团队分享接口 API 黑盒测试。
 * 需要先创建团队才能测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("团队分享接口测试")
class TeamShareApiTest extends ApiTestBase {

    // ==================== POST /api/v1/team/{teamId}/shares ====================

    @Test
    @DisplayName("创建团队分享 - 团队不存在")
    void createTeamShareTeamNotFound() {
        givenAuth()
                .body(Map.of("fileId", 1, "accessType", 0, "expireDays", 7))
                .when()
                .post("/api/v1/team/99999/shares")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("创建团队分享 - 未登录返回403")
    void createTeamShareUnauthorized() {
        givenNoAuth()
                .body(Map.of("fileId", 1, "accessType", 0))
                .when()
                .post("/api/v1/team/1/shares")
                .then()
                .statusCode(403);
    }

    // ==================== GET /api/v1/team/{teamId}/shares ====================

    @Test
    @DisplayName("获取团队分享列表 - 团队不存在")
    void listTeamSharesTeamNotFound() {
        givenAuth()
                .when()
                .get("/api/v1/team/99999/shares")
                .then()
                .statusCode(anyOf(is(200), is(400), is(403)))
                .body("code", not(equalTo("00000")));
    }

    @Test
    @DisplayName("获取团队分享列表 - 未登录返回403")
    void listTeamSharesUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/team/1/shares")
                .then()
                .statusCode(403);
    }

    // ==================== DELETE /api/v1/team/{teamId}/shares/{shareId} ====================

    @Test
    @DisplayName("取消团队分享 - 未登录返回403")
    void cancelTeamShareUnauthorized() {
        givenNoAuth()
                .when()
                .delete("/api/v1/team/1/shares/1")
                .then()
                .statusCode(403);
    }
}
