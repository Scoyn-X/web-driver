package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 团队权限中的私密空间提醒响应VO
 */
@Data
@Schema(description = "私密空间提醒")
public class TeamPermissionPrivateSpaceReminderResponseVO {

    @Schema(description = "是否展示提醒", example = "false")
    private Boolean visible;

    @Schema(description = "提醒文案", example = "")
    private String message;

}
