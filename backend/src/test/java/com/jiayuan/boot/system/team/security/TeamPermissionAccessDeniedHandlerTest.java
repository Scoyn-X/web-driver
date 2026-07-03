package com.jiayuan.boot.system.team.security;

import com.jiayuan.boot.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 团队权限拒绝响应测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@DisplayName("团队权限拒绝响应测试")
class TeamPermissionAccessDeniedHandlerTest {

    @Test
    @DisplayName("PreAuthorize 拒绝时返回团队权限业务原因")
    void handle_writesTeamPermissionDeniedReason() throws Exception {
        TeamPermissionAccessDeniedHandler handler = new TeamPermissionAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(RequireTeamPerm.ACCESS_DENIED_RESULT_CODE_ATTRIBUTE,
                ResultCode.NO_PERMISSION_TO_USE_API);
        request.setAttribute(RequireTeamPerm.ACCESS_DENIED_MESSAGE_ATTRIBUTE, "团队不可用");

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"A0312\"");
        assertThat(response.getContentAsString()).contains("\"msg\":\"团队不可用\"");
    }
}
