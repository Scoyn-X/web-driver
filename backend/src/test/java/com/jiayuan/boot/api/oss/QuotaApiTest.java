package com.jiayuan.boot.api.oss;

import com.jiayuan.boot.api.base.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * 配额接口 API 黑盒测试。
 *
 * @author didongchen
 * @since 2026/06/06
 */
@DisplayName("配额接口测试")
class QuotaApiTest extends ApiTestBase {

    @Test
    @DisplayName("获取配额 - 成功")
    void getQuotaSuccess() {
        givenAuth()
                .when()
                .get("/api/v1/quota")
                .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("code", equalTo("00000"))
                .body("data.totalQuota", notNullValue())
                .body("data.usedSpace", notNullValue());
    }

    @Test
    @DisplayName("获取配额 - 未登录返回403")
    void getQuotaUnauthorized() {
        givenNoAuth()
                .when()
                .get("/api/v1/quota")
                .then()
                .statusCode(403);
    }
}
