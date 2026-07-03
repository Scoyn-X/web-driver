package com.jiayuan.boot.system.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 系统配置响应 VO。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@Builder
@Schema(description = "系统配置响应")
public class SystemConfigResponseVO {

    @Schema(description = "回收站保留秒数")
    private long trashRetentionSeconds;

    @Schema(description = "VIP降级私密空间宽限期秒数")
    private long privateGracePeriodSeconds;

    @Schema(description = "清理任务间隔秒数")
    private long cleanupIntervalSeconds;

    @Schema(description = "普通用户总配额")
    private String normalTotalQuota;

    @Schema(description = "普通用户总配数字节数")
    private long normalTotalQuotaBytes;

    @Schema(description = "普通用户单文件大小限制")
    private String normalSingleFileLimit;

    @Schema(description = "普通用户单文件大小限制字节数")
    private long normalSingleFileLimitBytes;

    @Schema(description = "下载限速触发阈值")
    private String downloadThrottleThreshold;

    @Schema(description = "下载限速触发阈值字节数")
    private long downloadThrottleThresholdBytes;

    @Schema(description = "普通用户下载限速")
    private String normalDownloadSpeed;

    @Schema(description = "普通用户下载限速字节/秒")
    private long normalDownloadBytesPerSecond;

    @Schema(description = "当前生效的 YAML 配置（只读参考）")
    private Map<String, Object> yamlDefaults;
}
