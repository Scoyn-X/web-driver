package com.jiayuan.boot.api.base;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST Assured API 黑盒测试基类。
 * <p>
 * 每个测试类启动完整 Spring Boot 上下文（随机端口），连接真实 MySQL / Redis / MinIO。
 * 通过 {@link BeforeAll} 注册随机账户并获取 JWT，供子类测试方法使用。
 * 测试数据通过独立 test profile 的数据库和 bucket 与 dev 环境隔离。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ApiTestBase {

    @LocalServerPort
    protected int port;

    /** 当前测试类用户的 JWT，已通过真实登录获取 */
    protected String jwtToken;

    /** 当前测试类用户的账户名（随机生成） */
    protected String accountName;

    /** 当前测试类用户的用户 ID */
    protected Long userId;

    /** 当前测试类用户的账户 ID */
    protected Long accountId;

    private static final String PASSWORD = "Pass1234";

    // -------- lifecycle --------

    @BeforeAll
    void baseSetup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        accountName = "apitest_" + UUID.randomUUID().toString().substring(0, 8);
        register(accountName, PASSWORD);
        login(accountName, PASSWORD);
    }

    // -------- spec builders --------

    /**
     * 返回携带当前用户 JWT 的请求规格（每个测试方法前刷新 baseURI/port）。
     */
    protected RequestSpecification givenAuth() {
        return given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    /**
     * 返回不携带 JWT 的请求规格（用于鉴权失败测试）。
     */
    protected RequestSpecification givenNoAuth() {
        return given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .filter(new ResponseLoggingFilter());
    }

    /**
     * 返回文件上传请求规格。
     */
    protected RequestSpecification givenMultipartAuth() {
        return given()
                .baseUri("http://localhost")
                .port(port)
                .contentType("multipart/form-data")
                .header("Authorization", "Bearer " + jwtToken)
                .filter(new ResponseLoggingFilter());
    }

    // -------- assertion helpers --------

    /**
     * 校验响应为成功（code=00000）。
     */
    protected static void assertSuccess(io.restassured.response.Response response) {
        response.then().body("code", equalTo("00000"));
    }

    /**
     * 校验响应为成功并提取 data 中的字段。
     */
    protected static String extractDataField(io.restassured.response.Response response, String jsonPath) {
        assertSuccess(response);
        return response.jsonPath().getString("data." + jsonPath);
    }

    /**
     * 校验响应为成功并提取 data 中的整数字段。
     */
    protected static Long extractDataLong(io.restassured.response.Response response, String jsonPath) {
        assertSuccess(response);
        return response.jsonPath().getLong("data." + jsonPath);
    }

    // -------- internal helpers --------

    private void register(String name, String password) {
        given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "nickname", "API测试员",
                        "accountName", name,
                        "password", password,
                        "email", name + "@test.com",
                        "accountType", "personal"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo("00000"));
    }

    @SuppressWarnings("unchecked")
    private void login(String name, String password) {
        io.restassured.response.Response resp = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(Map.of("accountName", name, "password", password))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo("00000"))
                .extract()
                .response();

        jwtToken = resp.jsonPath().getString("data.token");
        userId = resp.jsonPath().getLong("data.userId");
        accountId = resp.jsonPath().getLong("data.accountId");
    }
}
