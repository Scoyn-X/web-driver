package com.jiayuan.boot.api.counter;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 访问计数接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("访问计数接口测试")
class VisitApiTest extends ApiTestBase {

    @Test
    @DisplayName("获取访问次数 - 成功")
    void getVisitCountSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/visits")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("增加访问次数 - 成功")
    void incrementVisitCountSuccess() {
        givenAuth()
                .when()
                .post("/api/v1/visits")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("重置访问次数 - 成功")
    void resetVisitCountSuccess() {
        givenAuth()
                .when()
                .delete("/api/v1/visits")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"));
    }

    @Test
    @DisplayName("获取访问次数 - 未登录返回403")
    void getVisitCountUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/visits")
                .then()
                .statusCode(403);
    }
}
