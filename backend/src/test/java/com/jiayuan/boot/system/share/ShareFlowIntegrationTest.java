package com.jiayuan.boot.system.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分享功能全链路集成测试。
 * <p>
 * 启动完整 Spring Boot 上下文（含 Security JWT 过滤器、MyBatis-Plus、MinIO 客户端），
 * 以 HTTP 方式真实调用全部接口：注册 → 登录 → 上传 → 创建分享 → 匿名访问 → 校验提取码 → 后端受控下载。
 * 需要 MySQL / Redis / MinIO 通过 {@code docker compose} 启动（dev profile）。
 * 默认单元测试门禁不执行该用例；本地完成基础设施启动后可添加 {@code -Djiayuan.integration=true}。
 * 每次运行使用随机账户名避免数据冲突。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "jiayuan.integration", matches = "true",
        disabledReason = "需要 backend/docker 中的 MySQL、Redis、MinIO 基础设施")
@DisplayName("分享功能全链路集成测试")
class ShareFlowIntegrationTest {

    private static final String NICK = "integration_tester";
    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String ACCOUNT_NAME = "it_user_" + SUFFIX;
    private static final String EMAIL = "it_" + SUFFIX + "@example.com";
    private static final String PASSWORD = "pass1234";

    private static String jwtToken;
    private static Long fileId;
    private static String shareToken;
    private static String extractCode;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @Order(1)
    @DisplayName("注册账户")
    void register() throws Exception {
        Map<String, String> body = Map.of(
                "nickname", NICK,
                "accountName", ACCOUNT_NAME,
                "password", PASSWORD,
                "email", EMAIL,
                "accountType", "personal");
        ResponseEntity<String> resp = restTemplate.postForEntity(url("/api/v1/auth/register"), body, String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("00000");
    }

    @Test
    @Order(2)
    @DisplayName("登录拿 JWT")
    void login() throws Exception {
        Map<String, String> body = Map.of("accountName", ACCOUNT_NAME, "password", PASSWORD);
        ResponseEntity<String> resp = restTemplate.postForEntity(url("/api/v1/auth/login"), body, String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("00000");
        jwtToken = json.get("data").get("token").asText();
        assertThat(jwtToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("上传文件")
    void uploadFile() throws Exception {
        byte[] fileContent = ("integration test content " + SUFFIX).getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "report-" + SUFFIX + ".txt";
            }
        });

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(parts, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(url("/api/v1/files"), request, String.class);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("00000");

        // 上传响应只含 name + url；通过 listFiles 拿到 fileId
        // 列表响应已改为信封：{breadcrumb, items}
        ResponseEntity<String> listResp = restTemplate.exchange(
                url("/api/v1/files"), HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        JsonNode listJson = objectMapper.readTree(listResp.getBody());
        for (JsonNode f : listJson.get("data").get("items")) {
            if (("report-" + SUFFIX + ".txt").equals(f.get("originalName").asText())) {
                fileId = f.get("id").asLong();
                break;
            }
        }
        assertThat(fileId).isNotNull();
        // 根目录请求面包屑只含「根目录」
        JsonNode breadcrumb = listJson.get("data").get("breadcrumb");
        assertThat(breadcrumb.size()).isEqualTo(1);
        assertThat(breadcrumb.get(0).get("id").asLong()).isZero();
        assertThat(breadcrumb.get(0).get("name").asText()).isEqualTo("根目录");
    }

    @Test
    @Order(4)
    @DisplayName("创建分享（分享码访问，7 天有效）")
    void createShare() throws Exception {
        Map<String, Object> body = Map.of("fileId", fileId, "accessType", 1, "expireDays", 7);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
        ResponseEntity<String> resp = restTemplate.postForEntity(url("/api/v1/shares"), req, String.class);

        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("00000");
        shareToken = json.get("data").get("shareToken").asText();
        extractCode = json.get("data").get("extractCode").asText();

        assertThat(shareToken).hasSize(32);
        assertThat(extractCode).matches("[A-Z0-9]{4}");
        assertThat(json.get("data").get("statusDesc").asText()).isEqualTo("有效");
    }

    @Test
    @Order(5)
    @DisplayName("匿名访问分享页")
    void anonymousAccess() throws Exception {
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/api/v1/s/" + shareToken), String.class);
        JsonNode json = objectMapper.readTree(resp.getBody());

        assertThat(json.get("code").asText()).isEqualTo("00000");
        assertThat(json.get("data").get("requireExtractCode").asBoolean()).isTrue();
        assertThat(json.get("data").get("fileName").asText()).startsWith("report-");
        assertThat(json.get("data").get("isDirectory").asBoolean()).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("错误提取码被拒")
    void verifyWrongCode() throws Exception {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/api/v1/s/" + shareToken + "/verify"),
                Map.of("extractCode", "WRONG"), String.class);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("A0735");
    }

    @Test
    @Order(7)
    @DisplayName("正确提取码通过 + 后端受控 URL 下载")
    void verifyAndDownload() throws Exception {
        // verify
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                url("/api/v1/s/" + shareToken + "/verify"),
                Map.of("extractCode", extractCode), String.class);
        assertThat(objectMapper.readTree(verifyResp.getBody()).get("code").asText()).isEqualTo("00000");

        // get download url
        ResponseEntity<String> dlResp = restTemplate.getForEntity(
                url("/api/v1/s/" + shareToken + "/download?code=" + extractCode), String.class);
        JsonNode json = objectMapper.readTree(dlResp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("00000");
        String downloadUrl = json.get("data").get("downloadUrl").asText();
        assertThat(downloadUrl)
                .startsWith("http://")
                .contains("/api/v1/s/" + shareToken + "/download/file")
                .doesNotContain("X-Amz-Signature");

        // 真实下载（经后端中转，验证分享下载不再绕过限速控制）
        ResponseEntity<byte[]> fileResp = new TestRestTemplate().getForEntity(URI.create(downloadUrl), byte[].class);
        assertThat(fileResp.getStatusCode().is2xxSuccessful()).isTrue();
        String content = new String(fileResp.getBody(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("integration test content " + SUFFIX);
    }

    @Test
    @Order(8)
    @DisplayName("取消分享 → 列表中消失（逻辑删除），匿名访问返 SHARE_NOT_FOUND(A0731)")
    void cancelThenAccessFails() throws Exception {
        // 先查自己的分享拿 id
        ResponseEntity<String> listResp = restTemplate.exchange(
                url("/api/v1/shares"), HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        JsonNode shares = objectMapper.readTree(listResp.getBody()).get("data");
        long shareId = -1;
        for (JsonNode s : shares) {
            if (shareToken.equals(s.get("shareToken").asText())) {
                shareId = s.get("id").asLong();
                break;
            }
        }
        Assertions.assertTrue(shareId > 0);

        // 取消（语义已变更：逻辑删除）
        restTemplate.exchange(url("/api/v1/shares/" + shareId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), String.class);

        // 取消后该分享不应再出现在「我的分享」列表
        ResponseEntity<String> listAfter = restTemplate.exchange(
                url("/api/v1/shares"), HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        JsonNode sharesAfter = objectMapper.readTree(listAfter.getBody()).get("data");
        for (JsonNode s : sharesAfter) {
            assertThat(s.get("shareToken").asText()).isNotEqualTo(shareToken);
        }

        // 再访问应返 SHARE_NOT_FOUND（已删 → @TableLogic 自动过滤 → 等同未找到）
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/api/v1/s/" + shareToken), String.class);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asText()).isEqualTo("A0731");
    }

    @Test
    @Order(9)
    @DisplayName("未登录访问 /api/v1/shares 返 403")
    void anonymousListRejected() {
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/api/v1/shares"), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtToken);
        return h;
    }
}
