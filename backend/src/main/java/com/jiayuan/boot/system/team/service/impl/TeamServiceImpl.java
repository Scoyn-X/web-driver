package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.share.service.ShareService;
import com.jiayuan.boot.system.team.converter.TeamMemberConverter;
import com.jiayuan.boot.system.team.converter.TeamSpaceConverter;
import com.jiayuan.boot.system.team.mapper.TeamMemberMapper;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.enums.TeamStatus;
import com.jiayuan.boot.system.team.model.vo.TeamCreateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamQuotaResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;
import com.jiayuan.boot.system.team.service.TeamInvitationService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import com.jiayuan.boot.system.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队空间服务实现
 *
 * @author didongchen
 * @since 2026/05/17
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamServiceImpl.class);

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final TeamSpaceConverter teamSpaceConverter;
    private final TeamMemberConverter teamMemberConverter;
    private final TeamInvitationService teamInvitationService;
    private final TeamPermissionService teamPermissionService;
    private final TeamQuotaService teamQuotaService;
    private final ShareService shareService;
    private final TeamFileWriteService teamFileWriteService;

    /**
     * 创建团队空间，创建者自动成为 Owner。
     */
    @Override
    @Transactional
    public TeamResponseVO createTeam(TeamCreateRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentAccountId = SecurityUtils.getCurrentAccountId();

        TeamSpace team = teamSpaceConverter.toEntity(request);
        Long totalQuota = teamQuotaService.resolveOwnerTeamTotalQuota(currentUserId);
        teamSpaceConverter.toInitTeam(team, currentUserId, currentAccountId, totalQuota);
        teamSpaceMapper.insert(team);

        TeamMember ownerMember = teamMemberConverter.toOwnerMember(
                team.getId(), currentUserId, currentAccountId, LocalDateTime.now());
        teamMemberMapper.insert(ownerMember);
        teamPermissionService.reloadPermissionCache();

        log.info("团队空间创建成功 teamId={} teamName={} ownerId={}", team.getId(), team.getName(), currentUserId);
        return buildTeamVO(team);
    }

    /**
     * 根据 ID 查询团队详情。
     */
    @Override
    public TeamResponseVO getTeamById(Long teamId) {
        TeamSpace team = getTeamOrThrow(teamId);
        return buildTeamVO(team);
    }

    /**
     * 查询当前用户所属的团队列表。
     */
    @Override
    public List<TeamResponseVO> listUserTeams() {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();

        List<TeamMember> members = teamMemberMapper.selectActiveMembershipsByAccount(currentAccountId);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> teamIds = members.stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        Map<Long, String> roleMap = members.stream()
                .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getRole, (a, b) -> a));

        List<TeamSpace> teams = teamSpaceMapper.selectBatchIds(teamIds);
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载 Owner 信息和成员数
        Set<Long> ownerIds = teams.stream()
                .map(TeamSpace::getOwnerId)
                .collect(Collectors.toSet());
        Map<Long, String> ownerNameMap = sysUserMapper.selectBatchIds(ownerIds)
                .stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));

        return teams.stream()
                .map(t -> teamSpaceConverter.toTeamVO(t,
                        ownerNameMap.getOrDefault(t.getOwnerId(), "未知"),
                        roleMap.get(t.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 修改团队名称和描述，需 Owner 或 Admin 角色。
     */
    @Override
    @Transactional
    public TeamResponseVO updateTeam(Long teamId, TeamUpdateRequestVO request) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamSpace team = getTeamOrThrow(teamId);

        checkTeamActive(team);

        teamSpaceConverter.toUpdatedEntity(request, team);
        teamSpaceMapper.updateById(team);

        log.info("团队信息更新 teamId={} teamName={} operatorAccountId={}", teamId, team.getName(), currentAccountId);
        return buildTeamVO(team);
    }

    /**
     * 解散团队空间，仅 Owner 可操作。
     */
    @Override
    @Transactional
    public void dissolveTeam(Long teamId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamSpace team = getTeamOrThrow(teamId);

        checkTeamActive(team);
        if (!currentAccountId.equals(team.getOwnerAccountId())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "仅团队 Owner 可以解散团队");
        }

        List<TeamMember> activeMembers = teamMemberMapper.selectActiveMembersByTeam(teamId);
        teamFileWriteService.permanentlyDeleteTeamSpaceFiles(teamId);
        requireAffected(teamSpaceMapper.updateTeamStatus(teamId, TeamStatus.DISSOLVED.getValue()),
                "团队状态已变化，请刷新后重试");
        teamMemberMapper.updateActiveMembersStatusByTeam(teamId, MemberStatus.EXITED.getValue());
        teamInvitationService.markPendingAsTeamDissolved(teamId);
        shareService.invalidateTeamShares(teamId);
        teamPermissionService.reloadPermissionCache();

        log.info("团队空间已解散 teamId={} teamName={} ownerId={} affectedMembers={} operatorId={}",
                teamId, team.getName(), team.getOwnerId(), activeMembers.size(), currentUserId);
    }

    /**
     * 查询团队空间配额。
     */
    @Override
    public TeamQuotaResponseVO getTeamQuota(Long teamId) {
        TeamSpace team = getTeamOrThrow(teamId);
        return teamSpaceConverter.toQuotaVO(team);
    }

    // ==================== 内部方法 ====================

    /**
     * 查询团队，不存在则抛异常
     */
    private TeamSpace getTeamOrThrow(Long teamId) {
        TeamSpace team = teamSpaceMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
        return team;
    }

    /**
     * 检查团队是否处于正常状态
     */
    private void checkTeamActive(TeamSpace team) {
        if (!TeamStatus.ACTIVE.getValue().equals(team.getStatus())) {
            throw new BusinessException(ResultCode.ACCESS_PERMISSION_EXCEPTION, "团队已解散，无法操作");
        }
    }

    private void requireAffected(int affectedRows, String message) {
        if (affectedRows <= 0) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, message);
        }
    }

    /**
     * 构建团队响应 VO，所有对象映射均通过 converter 完成。
     */
    private TeamResponseVO buildTeamVO(TeamSpace team) {
        SysUser owner = sysUserMapper.selectById(team.getOwnerId());
        String ownerName = owner != null ? owner.getNickname() : "未知";

        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamMember self = teamMemberMapper.selectActiveMemberByAccount(team.getId(), currentAccountId);
        String role = self != null ? self.getRole() : null;

        return teamSpaceConverter.toTeamVO(team, ownerName, role);
    }
}
