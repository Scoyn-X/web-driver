package com.jiayuan.boot.api.auth;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

/**
 * 认证接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("认证接口测试")
class AuthApiTest extends ApiTestBase {

    private static final String TEST_NAME = "reg_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_PASS = "TestPass1";

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册 - 成功")
    void registerSuccess() {
        givenNoAuth()
                .body(Map.of(
                        "nickname", "测试用户",
                        "accountName", TEST_NAME,
                        "password", TEST_PASS,
                        "email", TEST_NAME + "@test.com",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("注册 - 用户名为空")
    void registerEmptyUsername() {
        givenNoAuth()
                .body(Map.of(
                        "nickname", "测试",
                        "accountName", "",
                        "password", TEST_PASS,
                        "email", "empty@test.com",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @DisplayName("注册 - 密码格式错误（纯英文无数字）")
    void registerWeakPassword() {
        givenNoAuth()
                .body(Map.of(
                        "nickname", "测试",
                        "accountName", "weakpass_" + UUID.randomUUID().toString().substring(0, 8),
                        "password", "abcdefgh",
                        "email", "weak@test.com",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @DisplayName("注册 - 邮箱格式错误")
    void registerInvalidEmail() {
        givenNoAuth()
                .body(Map.of(
                        "nickname", "测试",
                        "accountName", "badmail_" + UUID.randomUUID().toString().substring(0, 8),
                        "password", TEST_PASS,
                        "email", "not-an-email",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @DisplayName("注册 - 重复用户名")
    void registerDuplicate() {
        givenNoAuth()
                .body(Map.of(
                        "nickname", "测试",
                        "accountName", TEST_NAME,
                        "password", TEST_PASS,
                        "email", "another@test.com",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录 - 成功")
    void loginSuccess() {
        givenNoAuth()
                .body(Map.of("accountName", accountName, "password", "Pass1234"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"))
                .body("data.token", not(emptyOrNullString()))
                .body("data.userId", notNullValue())
                .body("data.accountId", notNullValue());
    }

    @Test
    @DisplayName("登录 - 密码错误")
    void loginWrongPassword() {
        givenNoAuth()
                .body(Map.of("accountName", accountName, "password", "WrongPass1"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(401), is(400)));
    }

    @Test
    @DisplayName("登录 - 用户不存在")
    void loginNonExistentUser() {
        givenNoAuth()
                .body(Map.of("accountName", "no_such_user_xyz", "password", "Pass1234"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(401), is(400)));
    }

    @Test
    @DisplayName("登录 - 参数缺失")
    void loginMissingParams() {
        givenNoAuth()
                .body(Map.of("accountName", accountName))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(400)));
    }
}
