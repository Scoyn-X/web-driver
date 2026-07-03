package com.jiayuan.boot.system.oss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 回收站保留期与自动清理配置。
 *
 * @param cleanupInterval      Quartz 清理周期
 * @param privateGracePeriod   VIP 降级后的私密空间宽限期
 * @param privateTrashRetention 私密空间宽限期结束后进入回收站的保留时长
 * @param cleanupBatchSize     单次清理批量大小
 * @author charleslam
 * @since 2026/05/22
 */
@ConfigurationProperties(prefix = "lab3.retention")
public record TrashRetentionProperties(Duration cleanupInterval,
                                       Duration privateGracePeriod,
                                       Duration privateTrashRetention,
                                       int cleanupBatchSize) {

    private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_PRIVATE_GRACE_PERIOD = Duration.ofDays(3);
    private static final Duration DEFAULT_PRIVATE_TRASH_RETENTION = Duration.ofDays(3);
    private static final int DEFAULT_BATCH_SIZE = 100;

    public TrashRetentionProperties {
        cleanupInterval = defaultDuration(cleanupInterval, DEFAULT_CLEANUP_INTERVAL);
        privateGracePeriod = defaultDuration(privateGracePeriod, DEFAULT_PRIVATE_GRACE_PERIOD);
        privateTrashRetention = defaultDuration(privateTrashRetention, DEFAULT_PRIVATE_TRASH_RETENTION);
        cleanupBatchSize = cleanupBatchSize > 0 ? cleanupBatchSize : DEFAULT_BATCH_SIZE;
    }

    /**
     * Quartz trigger 使用的毫秒周期。
     *
     * @return 至少 1 秒的执行间隔
     */
    public long cleanupIntervalMillis() {
        return Math.max(Duration.ofSeconds(1).toMillis(), cleanupInterval.toMillis());
    }

    private static Duration defaultDuration(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
