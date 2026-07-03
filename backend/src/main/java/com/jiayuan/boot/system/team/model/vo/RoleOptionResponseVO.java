package com.jiayuan.boot.system.team.model.vo;

import com.jiayuan.boot.system.team.model.enums.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 团队角色选项响应对象。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@Schema(description = "团队角色选项响应")
public class RoleOptionResponseVO {

    @Schema(description = "角色编码", example = "Admin")
    @NotNull(message = "角色编码不能为空")
    private MemberRole code;

    @Schema(description = "角色名称", example = "管理员")
    @NotBlank(message = "角色名称不能为空")
    private String name;

    @Schema(description = "角色说明", example = "可管理成员并维护团队文件")
    private String description;

    @Schema(description = "当前场景是否可分配", example = "true")
    @NotNull(message = "是否可分配不能为空")
    private Boolean assignable;

    @Schema(description = "角色包含的权限点")
    @NotNull(message = "角色权限点不能为空")
    private List<String> permissions;
}
