package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建团队邀请请求VO。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@Data
@Schema(description = "创建团队邀请请求")
public class CreateInvitationRequestVO {

    @Schema(description = "被邀请用户ID，兼容旧客户端；新客户端使用 inviteeAccountId", example = "10002")
    private Long inviteeUserId;

    @Schema(description = "被邀请账户ID，发起邀请时必填", example = "20002")
    private Long inviteeAccountId;

    @Schema(description = "目标角色(Admin/Editor/Viewer)，发起邀请时必填", example = "Editor")
    private String roleCode;

    @Schema(description = "邀请有效期秒数，默认86400秒（24小时）", example = "3600")
    private Long expireSeconds;

    @Schema(description = "邀请备注", example = "加入课程项目团队")
    private String reason;
}
