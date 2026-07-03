package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 团队邀请动作响应VO。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Data
@Schema(description = "团队邀请动作响应")
public class InvitationActionResponseVO {

    @Schema(description = "邀请动作", example = "INVITE",
            allowableValues = {"INVITE", "ACCEPT", "REJECT", "REVOKE"})
    private String action;

    @Schema(description = "邀请状态", example = "PENDING",
            allowableValues = {"PENDING", "ACCEPTED", "REJECTED", "REVOKED", "EXPIRED", "TEAM_DISSOLVED"})
    private String status;

    @Schema(description = "邀请信息")
    private InvitationResponseVO invitation;

    @Schema(description = "接受邀请后生成的成员信息")
    private TeamMemberResponseVO member;

    @Schema(description = "处理结果说明", example = "邀请已发起")
    private String message;
}
