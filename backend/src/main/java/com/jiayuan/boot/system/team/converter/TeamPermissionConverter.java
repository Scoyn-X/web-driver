package com.jiayuan.boot.system.team.converter;

import com.jiayuan.boot.system.team.model.bo.TeamPermissionBuildBO;
import com.jiayuan.boot.system.team.model.entity.TeamPermission;
import com.jiayuan.boot.system.team.model.entity.TeamRole;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.vo.PermissionResponseVO;
import com.jiayuan.boot.system.team.model.vo.RoleOptionResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 团队权限对象转换器。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Mapper(componentModel = "spring", imports = {MemberRole.class})
public interface TeamPermissionConverter {

    /**
     * 转换系统权限点响应对象。
     *
     * @param permission 权限点实体
     * @return 系统权限点响应对象
     */
    @Mapping(target = "code", source = "permission.permission")
    @Mapping(target = "name", source = "permission.permission", qualifiedByName = "permissionName")
    @Mapping(target = "group", source = "permission.permission", qualifiedByName = "permissionGroup")
    @Mapping(target = "description", source = "permission.permission", qualifiedByName = "permissionDescription")
    PermissionResponseVO toPermissionResponseVO(TeamPermission permission);

    /**
     * 转换团队角色选项响应对象。
     *
     * @param role        团队角色实体
     * @param permissions 角色权限点
     * @return 团队角色选项响应对象
     */
    @Mapping(target = "code", expression = "java(MemberRole.fromValue(role.getRole()))")
    @Mapping(target = "name", source = "role.label")
    @Mapping(target = "description", source = "role.role", qualifiedByName = "roleDescription")
    @Mapping(target = "assignable", expression = "java(toRoleAssignable(role.getRole()))")
    RoleOptionResponseVO toRoleOptionResponseVO(TeamRole role, List<String> permissions);

    /**
     * 转换团队权限响应对象。
     *
     * @param build 团队权限响应构造参数
     * @return 团队权限响应对象
     */
    @Mapping(target = "teamId", source = "team.id")
    @Mapping(target = "teamName", source = "team.name")
    @Mapping(target = "teamStatus", source = "team.status")
    @Mapping(target = "role", source = "member.role")
    @Mapping(target = "privateSpaceReminder", ignore = true)
    TeamPermissionResponseVO toTeamPermissionResponseVO(TeamPermissionBuildBO build);

    /**
     * 转换权限点名称。
     *
     * @param code 权限点编码
     * @return 权限点名称
     */
    @Named("permissionName")
    default String toPermissionName(String code) {
        return switch (code) {
            case "team:manage" -> "管理团队";
            case "team:dissolve" -> "解散团队";
            case "owner:transfer" -> "转让所有权";
            case "member:invite" -> "邀请成员";
            case "member:remove" -> "移除成员";
            case "role:update" -> "修改角色";
            case "file:list" -> "查看文件";
            case "file:detail" -> "查看详情";
            case "file:download" -> "下载文件";
            case "file:upload" -> "上传文件";
            case "file:move" -> "移动文件";
            case "file:copy" -> "复制文件";
            case "file:delete" -> "删除文件";
            case "share:create" -> "创建分享";
            case "share:manage" -> "管理分享";
            case "trash:list" -> "查看回收站";
            case "trash:restore" -> "恢复回收站";
            case "trash:delete" -> "删除回收站";
            case "file:transfer:to-personal" -> "转存到个人";
            case "file:transfer:to-team" -> "转存到团队";
            default -> code;
        };
    }

    /**
     * 转换权限点分组。
     *
     * @param code 权限点编码
     * @return 权限点分组
     */
    @Named("permissionGroup")
    default String toPermissionGroup(String code) {
        if (code == null) {
            return "其他";
        }
        if (code.startsWith("team:") || code.startsWith("owner:")) {
            return "团队管理";
        }
        if (code.startsWith("member:") || code.startsWith("role:")) {
            return "团队成员";
        }
        if (code.startsWith("share:")) {
            return "团队分享";
        }
        if (code.startsWith("trash:")) {
            return "团队回收站";
        }
        if (code.startsWith("file:transfer:")) {
            return "团队转存";
        }
        if (code.startsWith("file:")) {
            return "团队文件";
        }
        return "其他";
    }

    /**
     * 转换权限点说明。
     *
     * @param code 权限点编码
     * @return 权限点说明
     */
    @Named("permissionDescription")
    default String toPermissionDescription(String code) {
        return "允许执行：" + toPermissionName(code);
    }

    /**
     * 转换角色说明。
     *
     * @param role 角色编码
     * @return 角色说明
     */
    @Named("roleDescription")
    default String toRoleDescription(String role) {
        return switch (role) {
            case "Owner" -> "拥有团队最高权限";
            case "Admin" -> "可管理成员并维护团队文件";
            case "Editor" -> "可维护团队文件和分享";
            case "Viewer" -> "可查看和下载团队文件";
            default -> role;
        };
    }

    /**
     * 转换角色是否可分配。
     *
     * @param role 角色编码
     * @return 是否可分配
     */
    default Boolean toRoleAssignable(String role) {
        return !MemberRole.Owner.getValue().equals(role);
    }
}
