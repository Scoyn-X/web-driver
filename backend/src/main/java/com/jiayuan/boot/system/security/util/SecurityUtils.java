package com.jiayuan.boot.system.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security 工具类
 * <p>
 * 从 SecurityContextHolder 中获取当前登录用户和账户信息
 *
 * @author didongchen
 * @since 2026/04/10
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户ID
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new RuntimeException("未获取到当前登录用户信息");
    }

    /**
     * 获取当前登录账户 ID
     *
     * @return 账户ID
     */
    public static Long getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long accountId) {
            return accountId;
        }
        throw new RuntimeException("未获取到当前登录账户信息");
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String username) {
            return username;
        }
        throw new RuntimeException("未获取到当前登录用户信息");
    }

}
