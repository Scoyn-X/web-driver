package com.jiayuan.boot.system.team.security;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 团队权限表达式服务单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("RequireTeamPerm 单元测试")
class RequireTeamPermTest {

    private final TeamPermissionService teamPermissionService = mock(TeamPermissionService.class);
    private final RequireTeamPerm requireTeamPerm = new RequireTeamPerm(teamPermissionService);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无当前请求：缺少 teamId 时直接拒绝")
    void hasPerm_withoutRequestDenies() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(requireTeamPerm.hasPerm("file:list")).isFalse();

        verifyNoInteractions(teamPermissionService);
    }

    @Test
    @DisplayName("权限点为空：记录拒绝原因")
    void hasPerm_blankPermissionDeniesAndStoresReason() {
        MockHttpServletRequest request = requestWithTeamId("9");

        assertThat(requireTeamPerm.hasPerm(" ")).isFalse();

        assertThat(request.getAttribute(RequireTeamPerm.ACCESS_DENIED_RESULT_CODE_ATTRIBUTE))
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        assertThat(request.getAttribute(RequireTeamPerm.ACCESS_DENIED_MESSAGE_ATTRIBUTE))
                .isEqualTo("缺少团队权限点");
    }

    @Test
    @DisplayName("teamId 不是数字：记录缺少团队路径参数")
    void hasPerm_invalidTeamIdDenies() {
        MockHttpServletRequest request = requestWithTeamId("abc");

        assertThat(requireTeamPerm.hasPerm("file:list")).isFalse();

        assertThat(request.getAttribute(RequireTeamPerm.ACCESS_DENIED_MESSAGE_ATTRIBUTE))
                .isEqualTo("缺少团队路径参数：teamId");
        verifyNoInteractions(teamPermissionService);
    }

    @Test
    @DisplayName("权限服务抛无 resultCode 的业务异常：回退为无权限码")
    void hasPerm_businessExceptionWithoutResultCodeUsesFallbackCode() {
        MockHttpServletRequest request = requestWithTeamId("9");
        doThrow(new BusinessException("deny"))
                .when(teamPermissionService).checkPermission(9L, 70L, "file:list");

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentAccountId).thenReturn(70L);

            assertThat(requireTeamPerm.hasPerm("file:list")).isFalse();
        }

        verify(teamPermissionService).checkPermission(9L, 70L, "file:list");
        assertThat(request.getAttribute(RequireTeamPerm.ACCESS_DENIED_RESULT_CODE_ATTRIBUTE))
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        assertThat(request.getAttribute(RequireTeamPerm.ACCESS_DENIED_MESSAGE_ATTRIBUTE))
                .isEqualTo("deny");
    }

    private static MockHttpServletRequest requestWithTeamId(Object teamId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("teamId", teamId));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
