package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队权限点实体
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_permission")
public class TeamPermission extends BaseEntity {

    /**
     * 权限点标识
     */
    private String permission;

}
