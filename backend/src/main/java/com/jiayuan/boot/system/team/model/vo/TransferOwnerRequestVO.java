package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 转让团队所有权请求VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "转让团队所有权请求")
public class TransferOwnerRequestVO {

    @Schema(description = "目标成员ID，兼容旧客户端；新客户端使用 targetAccountId", example = "20")
    private Long targetMemberId;

    @Schema(description = "目标账户ID", example = "20")
    private Long targetAccountId;

}
