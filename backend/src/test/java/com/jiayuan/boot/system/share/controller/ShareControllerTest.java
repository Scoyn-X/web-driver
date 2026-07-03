package com.jiayuan.boot.system.share.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import com.jiayuan.boot.system.share.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ShareController} 控制层测试（standalone MockMvc，不启动 Spring 上下文）。
 * <p>
 * 验证：HTTP 路由、请求体反序列化、Bean Validation、响应包装 {@code Result<T>}。
 * 鉴权由 Spring Security filter 负责，此处不测（集成测试覆盖）。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShareController 控制层测试")
class ShareControllerTest {

    private static final String REQUIRE_FILE_LIST = "@requireTeamPerm.hasPerm('file:list')";
    private static final String REQUIRE_SHARE_CREATE = "@requireTeamPerm.hasPerm('share:create')";
    private static final String REQUIRE_SHARE_MANAGE = "@requireTeamPerm.hasPerm('share:manage')";

    @Mock
    private ShareService shareService;

    @InjectMocks
    private ShareController shareController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shareController).build();
    }

    @Test
    @DisplayName("POST /api/v1/shares 合法请求返 ShareInfoResponseVO")
    void createShare_validRequest_returnsShareInfo() throws Exception {
        ShareCreateRequestVO req = patch(new ShareCreateRequestVO(),
                field("fileId", 1L),
                field("accessType", 0),
                field("expireDays", 7));

        ShareInfoResponseVO mockVO = patch(new ShareInfoResponseVO(),
                field("id", 500L),
                field("shareToken", "tok123"),
                field("statusDesc", "有效"),
                field("isDirectory", true));
        when(shareService.createShare(any(ShareCreateRequestVO.class))).thenReturn(mockVO);

        mockMvc.perform(post("/api/v1/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.id").value(500))
                .andExpect(jsonPath("$.data.shareToken").value("tok123"))
                .andExpect(jsonPath("$.data.statusDesc").value("有效"))
                .andExpect(jsonPath("$.data.isDirectory").value(true));

        verify(shareService).createShare(any(ShareCreateRequestVO.class));
    }

    @Test
    @DisplayName("GET /api/v1/shares 返用户分享列表")
    void listMyShares_returnsList() throws Exception {
        ShareInfoResponseVO v1 = patch(new ShareInfoResponseVO(),
                field("id", 1L),
                field("fileName", "a.txt"),
                field("isDirectory", false));
        ShareInfoResponseVO v2 = patch(new ShareInfoResponseVO(),
                field("id", 2L),
                field("fileName", "b"),
                field("isDirectory", true));
        when(shareService.listMyShares()).thenReturn(List.of(v1, v2));

        mockMvc.perform(get("/api/v1/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fileName").value("a.txt"))
                .andExpect(jsonPath("$.data[0].isDirectory").value(false))
                .andExpect(jsonPath("$.data[1].fileName").value("b"))
                .andExpect(jsonPath("$.data[1].isDirectory").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/shares/{id} 转发 pathVariable 给 service")
    void cancelShare_passesPathVariable() throws Exception {
        mockMvc.perform(delete("/api/v1/shares/{id}", 123L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        verify(shareService).cancelShare(eq(123L));
    }

    @Test
    @DisplayName("团队分享接口：Controller 声明 PreAuthorize 权限表达式")
    void teamShareEndpointsDeclarePreAuthorize() throws Exception {
        assertTeamSharePreAuthorize("createTeamShare",
                REQUIRE_SHARE_CREATE, Long.class, ShareCreateRequestVO.class);
        assertTeamSharePreAuthorize("listTeamShares", REQUIRE_SHARE_MANAGE, Long.class);
        assertTeamSharePreAuthorize("getTeamShare", REQUIRE_SHARE_MANAGE, Long.class, Long.class);
        assertTeamSharePreAuthorize("cancelTeamShare", REQUIRE_FILE_LIST, Long.class, Long.class);
    }

    private void assertTeamSharePreAuthorize(String methodName, String expression, Class<?>... parameterTypes)
            throws Exception {
        PreAuthorize annotation = TeamShareController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(methodName + " 权限注解").isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }

    private static <T> T patch(T target, FieldValue... values) {
        for (FieldValue value : values) {
            ReflectionTestUtils.setField(target, value.name(), value.value());
        }
        return target;
    }

    private static FieldValue field(String name, Object value) {
        return new FieldValue(name, value);
    }

    private record FieldValue(String name, Object value) {
    }
}
