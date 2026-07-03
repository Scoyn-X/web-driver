package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.security.mapper.SysAccountMapper;
import com.jiayuan.boot.system.security.model.entity.SysAccount;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.share.service.ShareService;
import com.jiayuan.boot.system.team.converter.TeamMemberConverter;
import com.jiayuan.boot.system.team.mapper.TeamMemberMapper;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.bo.TeamMemberDisplayBO;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.enums.TeamStatus;
import com.jiayuan.boot.system.team.model.vo.MemberRoleUpdateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferOwnerRequestVO;
import com.jiayuan.boot.system.team.service.MemberService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队成员管理服务实现
 *
 * @author didongchen
 * @since 2026/05/20
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAccountMapper sysAccountMapper;
    private final TeamMemberConverter teamMemberConverter;
    private final TeamPermissionService teamPermissionService;
    private final TeamQuotaService teamQuotaService;
    private final ShareService shareService;

    /**
     * 从团队中移除指定成员。
     */
    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        getActiveTeam(teamId);

        TeamMember target = getActiveMemberById(teamId, memberId);
        Long targetAccountId = target.getAccountId();
        if (MemberRole.Owner.getValue().equals(target.getRole())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "不能移除团队 Owner");
        }

        requireAffected(teamMemberMapper.updateActiveMemberStatus(
                teamId, targetAccountId, MemberStatus.REMOVED.getValue()), "团队成员状态已变化，请刷新后重试");
        shareService.invalidateTeamSharesByCreator(teamId, targetAccountId);
        teamPermissionService.reloadPermissionCache();

        log.info("团队成员被移除 teamId={} operatorAccountId={} targetAccountId={} targetRole={}",
                teamId, currentAccountId, targetAccountId, target.getRole());
    }

    /**
     * 当前用户主动退出团队。
     */
    @Override
    @Transactional
    public void exitTeam(Long teamId) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        getActiveTeam(teamId);

        TeamMember self = getActiveMember(teamId, currentAccountId);
        if (MemberRole.Owner.getValue().equals(self.getRole())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION,
                    "Owner 不能直接退出团队，请先转让所有权");
        }

        requireAffected(teamMemberMapper.updateActiveMemberStatus(
                teamId, currentAccountId, MemberStatus.EXITED.getValue()), "团队成员状态已变化，请刷新后重试");
        shareService.invalidateTeamSharesByCreator(teamId, currentAccountId);
        teamPermissionService.reloadPermissionCache();

        log.info("成员退出团队 teamId={} accountId={} role={}", teamId, currentAccountId, self.getRole());
    }

    /**
     * 修改团队成员角色，返回更新后的成员信息。
     */
    @Override
    @Transactional
    public TeamMemberResponseVO updateMemberRole(Long teamId, Long memberId, MemberRoleUpdateRequestVO request) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamSpace team = getActiveTeam(teamId);

        String newRole = request.getRole();
        try {
            MemberRole.valueOf(newRole);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "无效的角色");
        }
        if (MemberRole.Owner.name().equals(newRole)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将成员角色改为 Owner");
        }

        TeamMember target = getActiveMemberById(teamId, memberId);
        Long targetAccountId = target.getAccountId();
        String oldRole = target.getRole();
        if (MemberRole.Owner.getValue().equals(oldRole)) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "不能修改 Owner 的角色");
        }

        requireAffected(teamMemberMapper.updateActiveMemberRole(teamId, targetAccountId, newRole),
                "团队成员状态已变化，请刷新后重试");
        teamPermissionService.reloadPermissionCache();

        log.info("成员角色已修改 teamId={} operatorAccountId={} targetAccountId={} oldRole={} newRole={}",
                teamId, currentAccountId, targetAccountId, oldRole, newRole);

        UserBriefBO user = sysUserMapper.selectUserBriefById(target.getUserId());
        SysAccount account = sysAccountMapper.selectById(target.getAccountId());
        return toMemberVO(target, team, newRole, user, account != null ? account.getAccountName() : null);
    }

    /**
     * Owner 转让团队所有权。
     */
    @Override
    @Transactional
    public void transferOwner(Long teamId, TransferOwnerRequestVO request) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamSpace team = getActiveTeam(teamId);

        if (!currentAccountId.equals(team.getOwnerAccountId())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "仅团队 Owner 可以转让所有权");
        }

        TeamMember target = resolveTransferTarget(teamId, request);
        Long targetAccountId = target.getAccountId();
        if (currentAccountId.equals(targetAccountId)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将团队所有权转让给自己");
        }

        getActiveMember(teamId, currentAccountId);
        requireAffected(teamMemberMapper.updateActiveMemberRole(teamId, targetAccountId, MemberRole.Owner.getValue()),
                "目标成员状态已变化，请刷新后重试");
        requireAffected(teamMemberMapper.updateActiveMemberRole(teamId, currentAccountId, MemberRole.Admin.getValue()),
                "当前成员状态已变化，请刷新后重试");
        requireAffected(teamSpaceMapper.updateOwner(teamId, target.getUserId(), target.getAccountId()),
                "团队状态已变化，请刷新后重试");
        teamQuotaService.syncTeamTotalQuotaByOwner(teamId, target.getUserId());
        teamPermissionService.reloadPermissionCache();

        log.info("团队所有权已转让 teamId={} oldOwnerAccountId={} newOwnerAccountId={}",
                teamId, currentAccountId, targetAccountId);
    }

    /**
     * 列出团队成员，需团队成员身份。
     */
    @Override
    public List<TeamMemberResponseVO> listMembers(Long teamId) {
        TeamSpace team = getActiveTeam(teamId);

        List<TeamMember> members = teamMemberMapper.selectActiveMembersByTeam(teamId);

        // 收集所有需要查询用户信息的 ID：成员 + Owner
        Set<Long> userIds = members.stream()
                .map(TeamMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Long ownerId = team.getOwnerId();
        if (ownerId != null) {
            userIds.add(ownerId);
        }

        Set<Long> accountIds = members.stream()
                .map(TeamMember::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Long ownerAccountId = team.getOwnerAccountId();
        if (ownerAccountId != null) {
            accountIds.add(ownerAccountId);
        }

        Map<Long, UserBriefBO> userBriefMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectUserBriefByIds(new ArrayList<>(userIds))
                        .stream()
                        .collect(Collectors.toMap(UserBriefBO::getUserId, u -> u, (a, b) -> a));
        Map<Long, String> accountNameMap = accountIds.isEmpty()
                ? Collections.emptyMap()
                : sysAccountMapper.selectBatchIds(accountIds).stream()
                        .collect(Collectors.toMap(SysAccount::getId, SysAccount::getAccountName, (a, b) -> a));

        List<TeamMemberResponseVO> result = new ArrayList<>();

        // Owner（不在 team_member 表中，从 team_space.owner_id 构建）
        Long syntheticOwnerAccountId = ownerAccountId;
        if (syntheticOwnerAccountId == null && ownerId != null) {
            UserBriefBO ownerBrief = userBriefMap.get(ownerId);
            syntheticOwnerAccountId = ownerBrief == null ? null : ownerBrief.getAccountId();
        }
        if (ownerId != null
                && syntheticOwnerAccountId != null
                && !accountIdsHasMemberWithAccountId(members, syntheticOwnerAccountId)) {
            TeamMember ownerMember = teamMemberConverter.toOwnerMember(
                    teamId, ownerId, syntheticOwnerAccountId, null);
            result.add(toMemberVO(ownerMember, team, null, userBriefMap.get(ownerId),
                    accountNameMap.get(syntheticOwnerAccountId)));
        }

        // 普通成员
        for (TeamMember member : members) {
            UserBriefBO userBrief = userBriefMap.get(member.getUserId());
            result.add(toMemberVO(member, team, null, userBrief, accountNameMap.get(member.getAccountId())));
        }
        return result;
    }

    private TeamMemberResponseVO toMemberVO(TeamMember member, TeamSpace team, String role,
                                            UserBriefBO userBrief, String accountName) {
        String username = userBrief != null ? userBrief.getNickname() : "未知";
        String resolvedAccountName = accountName != null
                ? accountName
                : userBrief != null ? userBrief.getAccountName() : "未知";
        return teamMemberConverter.toMemberVO(TeamMemberDisplayBO.builder()
                .member(member)
                .team(team)
                .role(role == null ? member.getRole() : role)
                .username(username)
                .accountName(resolvedAccountName)
                .email(userBrief == null ? null : userBrief.getEmail())
                .build());
    }

    private static boolean accountIdsHasMemberWithAccountId(List<TeamMember> members, Long accountId) {
        return members.stream().anyMatch(m -> accountId.equals(m.getAccountId()));
    }

    // ==================== 内部方法 ====================

    private TeamSpace getActiveTeam(Long teamId) {
        TeamSpace team = teamSpaceMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
        if (!TeamStatus.ACTIVE.getValue().equals(team.getStatus())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "团队已解散，无法操作");
        }
        return team;
    }

    private TeamMember getActiveMember(Long teamId, Long accountId) {
        TeamMember member = teamMemberMapper.selectActiveMemberByAccount(teamId, accountId);
        if (member == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队成员不存在或已失效");
        }
        return member;
    }

    private TeamMember getActiveMemberById(Long teamId, Long memberId) {
        // 优先按成员记录ID查找
        TeamMember member = teamMemberMapper.selectById(memberId);
        if (member != null
                && Objects.equals(teamId, member.getTeamId())
                && MemberStatus.ACTIVE.getValue().equals(member.getStatus())) {
            return member;
        }
        // 兼容前端传accountId的场景：按团队+账户ID查找有效成员
        member = teamMemberMapper.selectActiveMemberByAccount(teamId, memberId);
        if (member == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队成员不存在或已失效");
        }
        return member;
    }

    private TeamMember resolveTransferTarget(Long teamId, TransferOwnerRequestVO request) {
        if (request.getTargetAccountId() != null) {
            return getActiveMember(teamId, request.getTargetAccountId());
        }
        if (request.getTargetMemberId() != null) {
            return getActiveMemberById(teamId, request.getTargetMemberId());
        }
        throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "目标账户ID不能为空");
    }

    private void requireAffected(int affectedRows, String message) {
        if (affectedRows <= 0) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, message);
        }
    }
}
