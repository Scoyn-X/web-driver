package com.jiayuan.boot.system.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * VIP 状态切换请求对象
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class VipUpdateRequestVO {

    @Schema(description = "是否切换为 VIP")
    @NotNull(message = "VIP 状态不能为空")
    private Boolean vip;

    @Schema(description = "切换原因")
    private String reason;
}
