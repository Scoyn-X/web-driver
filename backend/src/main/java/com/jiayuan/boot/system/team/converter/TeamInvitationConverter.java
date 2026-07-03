package com.jiayuan.boot.system.team.converter;

import com.jiayuan.boot.system.team.model.bo.PendingInvitationBO;
import com.jiayuan.boot.system.team.model.entity.TeamInvitation;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.InvitationStatus;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.vo.CreateInvitationRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionResponseVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 团队邀请对象转换器。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Mapper(componentModel = "spring", imports = {InvitationStatus.class, MemberStatus.class})
public interface TeamInvitationConverter {

    /**
     * 转换创建邀请请求为统一邀请动作请求。
     *
     * @param request 创建邀请请求
     * @return 邀请动作请求
     */
    @Mapping(target = "action", constant = "INVITE")
    @Mapping(target = "invitationId", ignore = true)
    InvitationActionRequestVO toInviteActionRequest(CreateInvitationRequestVO request);

    /**
     * 转换待处理邀请实体。
     *
     * @param pendingInvitation 待创建邀请信息
     * @return 邀请实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(InvitationStatus.PENDING.getValue())")
    @Mapping(target = "expireAt", source = "expireAt")
    @Mapping(target = "flowableInstanceId", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TeamInvitation toPendingInvitation(PendingInvitationBO pendingInvitation);

    /**
     * 转换有效团队成员实体。
     *
     * @param invitation 邀请实体
     * @param joinedAt   加入时间
     * @return 团队成员实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "invitation.inviteeId")
    @Mapping(target = "accountId", source = "invitation.inviteeAccountId")
    @Mapping(target = "role", source = "invitation.targetRole")
    @Mapping(target = "status", expression = "java(MemberStatus.ACTIVE.getValue())")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TeamMember toActiveMember(TeamInvitation invitation, LocalDateTime joinedAt);

    /**
     * 转换邀请响应对象。
     *
     * @param invitation 邀请实体
     * @param team       团队空间
     * @param inviter    邀请人信息
     * @param invitee    被邀请人信息
     * @return 邀请响应对象
     */
    @Mapping(target = "id", source = "invitation.id")
    @Mapping(target = "teamId", source = "invitation.teamId")
    @Mapping(target = "teamName", source = "team.name")
    @Mapping(target = "inviterId", source = "invitation.inviterId")
    @Mapping(target = "inviterAccountId", source = "invitation.inviterAccountId")
    @Mapping(target = "inviterName", source = "inviter.nickname")
    @Mapping(target = "inviteeId", source = "invitation.inviteeId")
    @Mapping(target = "inviteeAccountId", source = "invitation.inviteeAccountId")
    @Mapping(target = "inviteeName", source = "invitee.nickname")
    @Mapping(target = "targetRole", source = "invitation.targetRole")
    @Mapping(target = "status", source = "invitation.status")
    @Mapping(target = "expireAt", source = "invitation.expireAt")
    @Mapping(target = "createTime", source = "invitation.createTime")
    InvitationResponseVO toInvitationResponseVO(TeamInvitation invitation, TeamSpace team,
                                                UserBriefBO inviter, UserBriefBO invitee);

    /**
     * 转换团队成员响应对象。
     *
     * @param member 成员实体
     * @param user   用户基础信息
     * @return 团队成员响应对象
     */
    @Mapping(target = "id", source = "member.id")
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "accountId", source = "member.accountId")
    @Mapping(target = "accountName", source = "user.accountName")
    @Mapping(target = "username", source = "user.nickname")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "role", source = "member.role")
    @Mapping(target = "status", source = "member.status")
    @Mapping(target = "joinedAt", source = "member.joinedAt")
    TeamMemberResponseVO toMemberResponseVO(TeamMember member, UserBriefBO user);

    /**
     * 转换邀请动作响应对象。
     *
     * @param action     邀请动作
     * @param invitation 邀请响应
     * @param member     成员响应
     * @param message    结果说明
     * @return 邀请动作响应对象
     */
    @Mapping(target = "action", source = "action")
    @Mapping(target = "status", source = "invitation.status")
    @Mapping(target = "invitation", source = "invitation")
    @Mapping(target = "member", source = "member")
    @Mapping(target = "message", source = "message")
    InvitationActionResponseVO toActionResponseVO(String action,
                                                  InvitationResponseVO invitation,
                                                  TeamMemberResponseVO member,
                                                  String message);
}
