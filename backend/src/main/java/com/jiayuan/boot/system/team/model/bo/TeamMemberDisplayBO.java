package com.jiayuan.boot.system.team.model.bo;

import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import lombok.Builder;
import lombok.Getter;

/**
 * 团队成员展示转换业务对象。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@Getter
@Builder
public class TeamMemberDisplayBO {

    /**
     * 成员实体。
     */
    private final TeamMember member;

    /**
     * 团队实体。
     */
    private final TeamSpace team;

    /**
     * 展示角色。
     */
    private final String role;

    /**
     * 用户昵称。
     */
    private final String username;

    /**
     * 成员账户名。
     */
    private final String accountName;

    /**
     * 用户邮箱。
     */
    private final String email;
}
