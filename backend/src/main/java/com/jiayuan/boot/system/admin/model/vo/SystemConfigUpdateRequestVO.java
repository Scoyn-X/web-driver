package com.jiayuan.boot.system.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统配置更新请求 VO。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@Schema(description = "系统配置更新请求")
public class SystemConfigUpdateRequestVO {

    @Schema(description = "回收站保留秒数（1-31536000，最大365天）", example = "259200")
    private Long trashRetentionSeconds;

    @Schema(description = "VIP降级私密空间宽限期秒数（1-31536000，最大365天）", example = "259200")
    private Long privateGracePeriodSeconds;

    @Schema(description = "清理任务间隔秒数（1-86400）", example = "600")
    private Long cleanupIntervalSeconds;

    @Schema(description = "普通用户总配额字节数", example = "104857600")
    private Long normalTotalQuota;

    @Schema(description = "普通用户单文件大小限制字节数", example = "104857600")
    private Long normalSingleFileLimit;

    @Schema(description = "下载限速触发阈值字节数", example = "5242880")
    private Long downloadThrottleThreshold;

    @Schema(description = "普通用户下载限速字节/秒", example = "8388608")
    private Long normalDownloadBytesPerSecond;
}
