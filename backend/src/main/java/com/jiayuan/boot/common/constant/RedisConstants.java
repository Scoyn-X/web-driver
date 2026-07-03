package com.jiayuan.boot.common.constant;

/**
 * Redis 键名称常量
 * 
 * @author jiayuan
 * @since 2026/03/09
 */
public interface RedisConstants {

    /**
     * 限流相关键
     */
    interface RateLimiter {

        /**
         * IP 限流键
         * <p>
         * 示例：rate_limiter:ip:{ipAddress}
         */
        String IP = "rate_limiter:ip:{}";

    }

    /**
     * 分布式锁相关键
     */
    interface Lock {

        /**
         * 防重复提交键
         * <p>
         * 示例：lock:resubmit:{userIdentifier}:{requestIdentifier}
         */
        String RESUBMIT = "lock:resubmit:{}:{}";

    }

}
