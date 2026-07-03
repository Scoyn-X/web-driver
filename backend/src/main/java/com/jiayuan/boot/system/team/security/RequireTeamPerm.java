package com.jiayuan.boot.system.team.security;

import cn.hutool.core.util.StrUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.IResultCode;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 团队权限表达式服务，供 Spring Security 的 @PreAuthorize 调用。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Component("requireTeamPerm")
@RequiredArgsConstructor
public class RequireTeamPerm {

    private static final String TEAM_ID_PATH_VARIABLE = "teamId";
    public static final String ACCESS_DENIED_RESULT_CODE_ATTRIBUTE = "team.permission.denied.resultCode";
    public static final String ACCESS_DENIED_MESSAGE_ATTRIBUTE = "team.permission.denied.message";

    private final TeamPermissionService teamPermissionService;

    /**
     * 判断当前用户是否拥有指定团队权限点。
     *
     * @param permission 权限点字符串
     * @return 是否拥有权限
     */
    public boolean hasPerm(String permission) {
        Long teamId = resolveTeamId();
        if (teamId == null) {
            return deny(ResultCode.NO_PERMISSION_TO_USE_API, "缺少团队路径参数：teamId");
        }
        if (StrUtil.isBlank(permission)) {
            return deny(ResultCode.NO_PERMISSION_TO_USE_API, "缺少团队权限点");
        }
        try {
            teamPermissionService.checkPermission(teamId, SecurityUtils.getCurrentAccountId(), permission);
            return true;
        } catch (BusinessException ex) {
            IResultCode resultCode = ex.getResultCode() == null
                    ? ResultCode.NO_PERMISSION_TO_USE_API
                    : ex.getResultCode();
            return deny(resultCode, ex.getMessage());
        }
    }

    private boolean deny(IResultCode resultCode, String message) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(ACCESS_DENIED_RESULT_CODE_ATTRIBUTE, resultCode);
            request.setAttribute(ACCESS_DENIED_MESSAGE_ATTRIBUTE, message);
        }
        return false;
    }

    private Long resolveTeamId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> pathVariables = (Map<?, ?>) variables;
        Object teamId = pathVariables.get(TEAM_ID_PATH_VARIABLE);
        if (teamId == null) {
            return null;
        }
        try {
            return Long.valueOf(teamId.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
