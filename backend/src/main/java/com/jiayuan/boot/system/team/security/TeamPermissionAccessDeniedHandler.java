package com.jiayuan.boot.system.team.security;

import cn.hutool.json.JSONUtil;
import com.jiayuan.boot.common.result.IResultCode;
import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.common.result.ResultCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 团队权限拒绝处理器。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Component
public class TeamPermissionAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        IResultCode resultCode = resolveResultCode(request);
        String message = resolveMessage(request, resultCode);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().print(JSONUtil.toJsonStr(Result.failed(resultCode, message)));
        response.getWriter().flush();
    }

    private IResultCode resolveResultCode(HttpServletRequest request) {
        Object value = request.getAttribute(RequireTeamPerm.ACCESS_DENIED_RESULT_CODE_ATTRIBUTE);
        return value instanceof IResultCode ? (IResultCode) value : ResultCode.NO_PERMISSION_TO_USE_API;
    }

    private String resolveMessage(HttpServletRequest request, IResultCode resultCode) {
        Object value = request.getAttribute(RequireTeamPerm.ACCESS_DENIED_MESSAGE_ATTRIBUTE);
        if (value instanceof String) {
            String message = (String) value;
            if (!message.isBlank()) {
                return message;
            }
        }
        return resultCode.getMsg();
    }
}
