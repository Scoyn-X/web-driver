package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 团队邀请动作请求VO。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Data
@Schema(description = "团队邀请动作请求")
public class InvitationActionRequestVO {

    @Schema(description = "邀请动作(INVITE/ACCEPT/REJECT/REVOKE)", example = "INVITE",
            allowableValues = {"INVITE", "ACCEPT", "REJECT", "REVOKE"})
    @NotBlank(message = "邀请动作不能为空")
    private String action;

    @Schema(description = "邀请ID，处理已有邀请时必填", example = "1")
    private Long invitationId;

    @Schema(description = "被邀请用户ID，兼容旧客户端；新客户端使用 inviteeAccountId", example = "10002")
    private Long inviteeUserId;

    @Schema(description = "被邀请账户ID，发起邀请时必填", example = "20002")
    private Long inviteeAccountId;

    @Schema(description = "目标角色(Admin/Editor/Viewer)，发起邀请时必填", example = "Editor")
    private String roleCode;

    @Schema(description = "邀请有效期秒数，默认86400秒（24小时）", example = "3600")
    private Long expireSeconds;

    @Schema(description = "拒绝或撤销原因", example = "暂不加入")
    private String reason;
}
