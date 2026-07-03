package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建团队邀请请求VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "创建团队邀请请求")
public class InvitationCreateRequestVO {

    @Schema(description = "被邀请用户ID", example = "2")
    @NotNull(message = "被邀请用户ID不能为空")
    private Long inviteeId;

    @Schema(description = "目标角色(Admin/Editor/Viewer)，不能邀请为Owner", example = "Editor")
    @NotNull(message = "目标角色不能为空")
    private String targetRole;

}
