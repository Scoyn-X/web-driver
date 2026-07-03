package com.jiayuan.boot.system.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 系统可调参数运行时配置。
 * <p>
 * 默认值从 YAML 读取，运行时可通过 API 覆盖。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@Component
@ConfigurationProperties(prefix = "lab3.config")
public class SystemConfigProperties {

    /** 回收站保留秒数（默认 259200 秒 = 3 天） */
    private long trashRetentionSeconds = 259200;

    /** VIP降级私密空间宽限期秒数（默认 259200 秒 = 3 天） */
    private long privateGracePeriodSeconds = 259200;

    /** 清理任务间隔秒数（默认 30 秒，与 lab3.retention.cleanup-interval 一致） */
    private long cleanupIntervalSeconds = 30;

    /** 普通用户总配额字节数（默认 100 MB） */
    private long normalTotalQuota = 100L * 1024 * 1024;

    /** 普通用户单文件大小限制字节数（默认 100 MB） */
    private long normalSingleFileLimit = 100L * 1024 * 1024;

    /** 下载限速触发阈值字节数（默认 5 MB） */
    private long downloadThrottleThreshold = 5L * 1024 * 1024;

    /** 普通用户下载限速字节/秒（默认 512 KB/s） */
    private long normalDownloadBytesPerSecond = 512L * 1024;
}
