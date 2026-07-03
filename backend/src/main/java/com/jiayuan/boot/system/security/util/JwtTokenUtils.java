package com.jiayuan.boot.system.security.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.security.constant.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * JWT 请求上下文工具。
 *
 * @author charleslam
 * @since 2026/05/18
 */
public final class JwtTokenUtils {

    private JwtTokenUtils() {
    }

    /**
     * 获取当前请求的 Bearer token。
     *
     * @return JWT token 原文
     */
    public static String getCurrentBearerToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ResultCode.ACCESS_TOKEN_INVALID, "未获取到当前登录令牌");
        }
        return resolveBearerToken(attributes.getRequest());
    }

    /**
     * 获取当前请求 token 的 SHA-256 哈希。
     *
     * @return token 哈希
     */
    public static String getCurrentTokenHash() {
        return DigestUtil.sha256Hex(getCurrentBearerToken());
    }

    /**
     * 从请求头解析 Bearer token。
     *
     * @param request HTTP 请求
     * @return JWT token 原文
     */
    public static String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isBlank(authorization) || !authorization.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX)) {
            throw new BusinessException(ResultCode.ACCESS_TOKEN_INVALID, "未获取到当前登录令牌");
        }
        return authorization.substring(SecurityConstants.BEARER_TOKEN_PREFIX.length());
    }
}
