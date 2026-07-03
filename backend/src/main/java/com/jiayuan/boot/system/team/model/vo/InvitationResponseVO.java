package com.jiayuan.boot.system.team.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队邀请响应VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "团队邀请响应")
public class InvitationResponseVO {

    @Schema(description = "邀请ID", example = "1")
    private Long id;

    @Schema(description = "团队ID", example = "1")
    private Long teamId;

    @Schema(description = "团队名称", example = "软件工程第14组")
    private String teamName;

    @Schema(description = "团队简介")
    private String teamDescription;

    @Schema(description = "邀请人用户ID", example = "1")
    private Long inviterId;

    @Schema(description = "邀请人账户ID", example = "10")
    private Long inviterAccountId;

    @Schema(description = "邀请人用户名", example = "张三")
    private String inviterName;

    @Schema(description = "邀请人账户名", example = "zhangsan_work")
    private String inviterAccountName;

    @Schema(description = "被邀请人用户ID", example = "2")
    private Long inviteeId;

    @Schema(description = "被邀请人账户ID", example = "20")
    private Long inviteeAccountId;

    @Schema(description = "被邀请人用户名", example = "李四")
    private String inviteeName;

    @Schema(description = "被邀请人账户名", example = "lisi_work")
    private String inviteeAccountName;

    @Schema(description = "目标角色(Admin/Editor/Viewer)", example = "Editor")
    private String targetRole;

    @Schema(description = "邀请状态(PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED/TEAM_DISSOLVED)", example = "PENDING")
    private String status;

    @Schema(description = "过期时间", example = "2026-05-22 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;

    @Schema(description = "创建时间", example = "2026-05-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
