package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队空间实体
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_space")
public class TeamSpace extends BaseEntity {

    /**
     * 团队名称
     */
    private String name;

    /**
     * 团队描述
     */
    private String description;

    /**
     * 团队Owner用户ID
     */
    private Long ownerId;

    /**
     * 团队Owner账户ID
     */
    private Long ownerAccountId;

    /**
     * 团队状态(ACTIVE-正常 DISSOLVED-已解散)
     */
    private String status;

    /**
     * 团队总配额（字节）
     */
    private Long totalQuota;

    /**
     * 已使用空间（字节）
     */
    private Long usedSpace;

}
