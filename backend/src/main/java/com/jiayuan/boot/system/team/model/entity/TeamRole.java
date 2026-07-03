package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队角色定义实体
 *
 * @author didongchen
 * @since 2026/05/16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_role")
public class TeamRole extends BaseEntity {

    /**
     * 角色标识(Owner/Admin/Editor/Viewer)
     */
    private String role;

    /**
     * 角色显示名称
     */
    private String label;

}
