package com.jiayuan.boot.system.team.model.bo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 团队成员构造参数。
 *
 * @author charleslam
 * @since 2026/05/23
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class TeamMemberBuildBO {

    /** 团队ID */
    private Long teamId;

    /** 用户ID */
    private Long userId;

    /** 账户ID */
    private Long accountId;

    /** 团队角色 */
    private String role;

    /** 加入时间 */
    private LocalDateTime joinedAt;
}
