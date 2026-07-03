package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统权限点响应对象。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@Schema(description = "系统权限点响应")
public class PermissionResponseVO {

    @Schema(description = "权限点编码", example = "member:invite")
    @NotBlank(message = "权限点编码不能为空")
    private String code;

    @Schema(description = "权限点名称", example = "邀请成员")
    @NotBlank(message = "权限点名称不能为空")
    private String name;

    @Schema(description = "权限分组", example = "团队成员")
    @NotBlank(message = "权限分组不能为空")
    private String group;

    @Schema(description = "权限点说明", example = "允许向团队发起成员邀请")
    private String description;
}
