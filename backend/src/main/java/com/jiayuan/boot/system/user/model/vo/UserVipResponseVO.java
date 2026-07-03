package com.jiayuan.boot.system.user.model.vo;

import com.jiayuan.boot.system.user.model.enums.VipState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户 VIP 状态响应对象
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class UserVipResponseVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "VIP 状态")
    private VipState vipState;

    @Schema(description = "个人容量限制，VIP 为 null")
    private Long personalQuotaLimit;

    @Schema(description = "团队容量限制，VIP 为 null")
    private Long teamQuotaLimit;

    @Schema(description = "是否下载限速")
    private Boolean downloadLimited;

    @Schema(description = "单文件大小限制，VIP 为 null")
    private Long singleFileLimit;
}
