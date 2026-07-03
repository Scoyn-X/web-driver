package com.jiayuan.boot.system.team.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员响应VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "团队成员响应")
public class TeamMemberResponseVO {

    @Schema(description = "成员记录ID", example = "1")
    private Long id;

    @Schema(description = "团队ID", example = "1")
    private Long teamId;

    @Schema(description = "团队名称", example = "软件工程第14组")
    private String teamName;

    @Schema(description = "团队简介")
    private String teamDescription;

    @Schema(description = "用户ID", example = "2")
    private Long userId;

    @Schema(description = "成员账户ID", example = "20")
    private Long accountId;

    @Schema(description = "成员账户名", example = "lisi_work")
    private String accountName;

    @Schema(description = "用户名", example = "李四")
    private String username;

    @Schema(description = "用户邮箱", example = "lisi@example.com")
    private String email;

    @Schema(description = "团队角色(Owner/Admin/Editor/Viewer)", example = "Editor")
    private String role;

    @Schema(description = "成员状态(ACTIVE-正常 REMOVED-已被移除 EXITED-已退出)", example = "ACTIVE")
    private String status;

    @Schema(description = "加入时间", example = "2026-05-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinedAt;

}
