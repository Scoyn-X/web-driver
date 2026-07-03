package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 团队邀请实体
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_invitation")
public class TeamInvitation extends BaseEntity {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 邀请人用户ID
     */
    private Long inviterId;

    /**
     * 邀请人账户ID
     */
    private Long inviterAccountId;

    /**
     * 被邀请人用户ID
     */
    private Long inviteeId;

    /**
     * 被邀请人账户ID
     */
    private Long inviteeAccountId;

    /**
     * 目标角色(Owner/Admin/Editor/Viewer)
     */
    private String targetRole;

    /**
     * 邀请状态(PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED/TEAM_DISSOLVED)
     */
    private String status;

    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    /**
     * Flowable流程实例ID
     */
    private String flowableInstanceId;

    /**
     * 拒绝或撤销原因
     */
    private String reason;

}
