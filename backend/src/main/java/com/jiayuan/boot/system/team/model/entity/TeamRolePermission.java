package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队角色-权限关联实体
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_role_permission")
public class TeamRolePermission extends BaseEntity {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限点ID
     */
    private Long permissionId;

}
