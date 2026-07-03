package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改成员角色请求VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "修改成员角色请求")
public class MemberRoleUpdateRequestVO {

    @Schema(description = "新角色(Admin/Editor/Viewer)，不能修改为Owner", example = "Editor")
    @NotBlank(message = "角色不能为空")
    private String role;

}
