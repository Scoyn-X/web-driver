package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 团队权限响应VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "团队权限响应")
public class TeamPermissionResponseVO {

    @Schema(description = "团队ID", example = "1")
    @NotNull(message = "团队ID不能为空")
    private Long teamId;

    @Schema(description = "团队名称", example = "软件工程第14组")
    @NotBlank(message = "团队名称不能为空")
    private String teamName;

    @Schema(description = "团队状态(ACTIVE/DISSOLVED)", example = "ACTIVE")
    @NotBlank(message = "团队状态不能为空")
    private String teamStatus;

    @Schema(description = "当前用户角色(Owner/Admin/Editor/Viewer)", example = "Editor")
    @NotBlank(message = "当前用户角色不能为空")
    private String role;

    @Schema(description = "当前用户权限点列表")
    @NotEmpty(message = "当前用户权限点不能为空")
    private List<String> permissions;

    @Schema(description = "配额状态(NORMAL/OVER_LIMIT)", example = "NORMAL")
    @NotBlank(message = "配额状态不能为空")
    private String quotaState;

    @Schema(description = "VIP状态(VIP/NORMAL)", example = "NORMAL")
    @NotBlank(message = "VIP状态不能为空")
    private String vipState;

    @Schema(description = "单文件大小限制（字节），VIP不限时为空", example = "104857600")
    private Long singleFileLimit;

    @Schema(description = "下载是否限速", example = "true")
    @NotNull(message = "下载限速标记不能为空")
    private Boolean downloadLimited;

    @Schema(description = "私密空间提醒")
    private TeamPermissionPrivateSpaceReminderResponseVO privateSpaceReminder;

}
