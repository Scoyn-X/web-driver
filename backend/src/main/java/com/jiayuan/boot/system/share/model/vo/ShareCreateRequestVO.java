package com.jiayuan.boot.system.share.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建分享请求 VO
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@Schema(description = "创建分享请求")
public class ShareCreateRequestVO {

    @Schema(description = "被分享的文件/目录ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @Schema(description = "访问方式(0-全公开 1-分享码访问)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "访问方式不能为空")
    @Min(value = 0, message = "访问方式取值 0 或 1")
    private Integer accessType;

    @Schema(description = "有效天数(1/7/30)，null 表示永久有效", example = "7")
    private Integer expireDays;

}
