package com.jiayuan.boot.system.quota.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 配额信息视图对象
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Data
@Schema(description = "配额信息视图对象")
public class QuotaResponseVO {

    @Schema(description = "总配额（字节）", example = "1073741824")
    private Long totalQuota;

    @Schema(description = "已使用空间（字节）", example = "52428800")
    private Long usedSpace;

    @Schema(description = "剩余空间（字节）", example = "1021054976")
    private Long remainingSpace;

    @Schema(description = "总配额格式化展示", example = "1 GB")
    private String totalQuotaFormatted;

    @Schema(description = "已使用空间格式化展示", example = "50 MB")
    private String usedSpaceFormatted;

    @Schema(description = "剩余空间格式化展示", example = "973.57 MB")
    private String remainingSpaceFormatted;

}
