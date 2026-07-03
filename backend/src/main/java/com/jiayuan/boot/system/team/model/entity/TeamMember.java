package com.jiayuan.boot.system.team.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 团队成员实体
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_member")
public class TeamMember extends BaseEntity {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 团队角色(Owner/Admin/Editor/Viewer)
     */
    private String role;

    /**
     * 成员状态(ACTIVE-正常 REMOVED-已被移除 EXITED-已退出)
     */
    private String status;

    /**
     * 加入时间
     */
    private LocalDateTime joinedAt;

}
