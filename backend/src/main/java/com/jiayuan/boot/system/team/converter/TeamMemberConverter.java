package com.jiayuan.boot.system.team.converter;

import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.bo.TeamMemberBuildBO;
import com.jiayuan.boot.system.team.model.bo.TeamMemberDisplayBO;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 团队成员对象转换器（MapStruct）
 *
 * @author didongchen
 * @since 2026/05/17
 */
@Mapper(componentModel = "spring")
public interface TeamMemberConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TeamMember toMember(TeamMemberBuildBO build);

    /**
     * 创建团队时初始化 Owner 成员（角色 Owner，状态 ACTIVE）。
     */
    default TeamMember toOwnerMember(Long teamId, Long userId, Long accountId, LocalDateTime joinedAt) {
        TeamMember member = toMember(TeamMemberBuildBO.of(
                teamId, userId, accountId, MemberRole.Owner.getValue(), joinedAt));
        member.setStatus(MemberStatus.ACTIVE.getValue());
        return member;
    }

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "accountName", ignore = true)
    TeamMemberResponseVO toMemberVO(TeamMember member);

    /**
     * 转换团队成员响应对象并补充展示名称。
     *
     * @param member      成员实体
     * @param username    用户昵称
     * @param accountName 账户名
     * @return 成员响应对象
     */
    @Mapping(target = "username", source = "username")
    @Mapping(target = "accountName", source = "accountName")
    TeamMemberResponseVO toMemberVO(TeamMember member, String username, String accountName);

    /**
     * 转换团队成员响应对象并覆盖角色展示。
     *
     * @param member      成员实体
     * @param role        目标角色
     * @param username    用户昵称
     * @param accountName 账户名
     * @return 成员响应对象
     */
    @Mapping(target = "role", source = "role")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "accountName", source = "accountName")
    TeamMemberResponseVO toMemberVO(TeamMember member, String role, String username, String accountName);

    /**
     * 转换团队成员展示响应对象。
     *
     * @param display 成员展示信息
     * @return 成员响应对象
     */
    @Mapping(target = "id", source = "member.id")
    @Mapping(target = "teamId", source = "team.id")
    @Mapping(target = "teamName", source = "team.name")
    @Mapping(target = "teamDescription", source = "team.description")
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "accountId", source = "member.accountId")
    @Mapping(target = "accountName", source = "accountName")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", source = "member.status")
    @Mapping(target = "joinedAt", source = "member.joinedAt")
    TeamMemberResponseVO toMemberVO(TeamMemberDisplayBO display);
}
