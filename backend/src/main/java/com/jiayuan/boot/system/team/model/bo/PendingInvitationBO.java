package com.jiayuan.boot.system.team.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待创建团队邀请业务对象。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Data
public class PendingInvitationBO {

    /**
     * 团队ID。
     */
    private Long teamId;

    /**
     * 邀请人用户ID。
     */
    private Long inviterId;

    /**
     * 邀请人账户ID。
     */
    private Long inviterAccountId;

    /**
     * 被邀请人用户ID。
     */
    private Long inviteeId;

    /**
     * 被邀请人账户ID。
     */
    private Long inviteeAccountId;

    /**
     * 目标角色。
     */
    private String targetRole;

    /**
     * 过期时间。
     */
    private LocalDateTime expireAt;

    /**
     * 创建待处理邀请业务对象。
     *
     * @param teamId     团队ID
     * @param inviterId        邀请人用户ID
     * @param inviterAccountId 邀请人账户ID
     * @param inviteeId        被邀请人用户ID
     * @param inviteeAccountId 被邀请人账户ID
     * @param targetRole       目标角色
     * @param expireAt         过期时间
     * @return 待创建团队邀请业务对象
     */
    public static PendingInvitationBO of(Long teamId, Long inviterId, Long inviterAccountId,
                                         Long inviteeId, Long inviteeAccountId,
                                         String targetRole, LocalDateTime expireAt) {
        PendingInvitationBO invitation = new PendingInvitationBO();
        invitation.setTeamId(teamId);
        invitation.setInviterId(inviterId);
        invitation.setInviterAccountId(inviterAccountId);
        invitation.setInviteeId(inviteeId);
        invitation.setInviteeAccountId(inviteeAccountId);
        invitation.setTargetRole(targetRole);
        invitation.setExpireAt(expireAt);
        return invitation;
    }
}
