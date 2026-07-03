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
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.enums.TeamStatus;
import com.jiayuan.boot.system.team.model.vo.TeamCreateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamQuotaResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;
import com.jiayuan.boot.system.team.service.TeamInvitationService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 团队空间服务单元测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamServiceImpl 单元测试")
class TeamServiceImplTest {

    private static final Long TEAM_ID = 9L;
    private static final Long OWNER_ID = 7L;
    private static final Long OWNER_ACCOUNT_ID = 70L;

    @Mock private TeamSpaceMapper teamSpaceMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private TeamSpaceConverter teamSpaceConverter;
    @Mock private TeamMemberConverter teamMemberConverter;
    @Mock private TeamInvitationService teamInvitationService;
    @Mock private TeamPermissionService teamPermissionService;
    @Mock private TeamQuotaService teamQuotaService;
    @Mock private ShareService shareService;
    @Mock private TeamFileWriteService teamFileWriteService;

    @Test
    @DisplayName("创建团队：Owner 成员创建后刷新权限缓存")
    void createTeam_reloadPermissionCacheAfterOwnerCreated() {
        TeamServiceImpl teamService = newTeamService();
        TeamCreateRequestVO request = new TeamCreateRequestVO();
        TeamSpace team = activeTeam();
        TeamMember owner = activeOwnerMember();
        SysUser ownerUser = new SysUser();
        ownerUser.setNickname("Owner");
        TeamResponseVO response = new TeamResponseVO();

        when(teamSpaceConverter.toEntity(request)).thenReturn(team);
        when(teamQuotaService.resolveOwnerTeamTotalQuota(OWNER_ID)).thenReturn(1073741824L);
        doAnswer(invocation -> {
            TeamSpace target = invocation.getArgument(0);
            target.setOwnerId(OWNER_ID);
            target.setStatus(TeamStatus.ACTIVE.getValue());
            target.setOwnerAccountId(OWNER_ACCOUNT_ID);
            target.setTotalQuota(invocation.getArgument(3));
            target.setUsedSpace(0L);
            return null;
        }).when(teamSpaceConverter).toInitTeam(eq(team), eq(OWNER_ID), eq(OWNER_ACCOUNT_ID), eq(1073741824L));
        when(teamMemberConverter.toOwnerMember(eq(TEAM_ID), eq(OWNER_ID), eq(OWNER_ACCOUNT_ID), any()))
                .thenReturn(owner);
        when(sysUserMapper.selectById(OWNER_ID)).thenReturn(ownerUser);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ACCOUNT_ID)).thenReturn(owner);
        when(teamSpaceConverter.toTeamVO(team, "Owner", MemberRole.Owner.getValue())).thenReturn(response);

        TeamResponseVO result;
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(OWNER_ID);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ACCOUNT_ID);

            result = teamService.createTeam(request);
        }

        assertThat(result).isSameAs(response);
        InOrder inOrder = inOrder(teamSpaceMapper, teamMemberMapper, teamPermissionService);
        inOrder.verify(teamSpaceMapper).insert(team);
        inOrder.verify(teamMemberMapper).insert(owner);
        inOrder.verify(teamPermissionService).reloadPermissionCache();
    }

    @Test
    @DisplayName("VIP Owner 创建团队：初始团队容量为无限")
    void createTeam_vipOwnerUsesUnlimitedQuota() {
        TeamServiceImpl teamService = newTeamService();
        TeamCreateRequestVO request = new TeamCreateRequestVO();
        TeamSpace team = activeTeam();
        TeamMember owner = activeOwnerMember();
        SysUser ownerUser = new SysUser();
        ownerUser.setNickname("Owner");
        TeamResponseVO response = new TeamResponseVO();

        when(teamSpaceConverter.toEntity(request)).thenReturn(team);
        when(teamQuotaService.resolveOwnerTeamTotalQuota(OWNER_ID)).thenReturn(Long.MAX_VALUE);
        doAnswer(invocation -> {
            TeamSpace target = invocation.getArgument(0);
            target.setOwnerId(OWNER_ID);
            target.setStatus(TeamStatus.ACTIVE.getValue());
            target.setOwnerAccountId(OWNER_ACCOUNT_ID);
            target.setTotalQuota(invocation.getArgument(3));
            target.setUsedSpace(0L);
            return null;
        }).when(teamSpaceConverter).toInitTeam(eq(team), eq(OWNER_ID), eq(OWNER_ACCOUNT_ID), eq(Long.MAX_VALUE));
        when(teamMemberConverter.toOwnerMember(eq(TEAM_ID), eq(OWNER_ID), eq(OWNER_ACCOUNT_ID), any()))
                .thenReturn(owner);
        when(sysUserMapper.selectById(OWNER_ID)).thenReturn(ownerUser);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ACCOUNT_ID)).thenReturn(owner);
        when(teamSpaceConverter.toTeamVO(team, "Owner", MemberRole.Owner.getValue())).thenReturn(response);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(OWNER_ID);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ACCOUNT_ID);

            assertThat(teamService.createTeam(request)).isSameAs(response);
        }

        verify(teamSpaceConverter).toInitTeam(team, OWNER_ID, OWNER_ACCOUNT_ID, Long.MAX_VALUE);
        verify(teamSpaceMapper).insert(team);
    }

    @Test
    @DisplayName("解散团队：成员退出后刷新权限缓存")
    void dissolveTeam_reloadPermissionCacheAfterMembersExited() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        TeamMember owner = activeOwnerMember();
        TeamMember editor = activeMember(8L, MemberRole.Editor.getValue());
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersByTeam(TEAM_ID)).thenReturn(List.of(owner, editor));
        when(teamSpaceMapper.updateTeamStatus(TEAM_ID, TeamStatus.DISSOLVED.getValue())).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(OWNER_ID);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ACCOUNT_ID);

            teamService.dissolveTeam(TEAM_ID);
        }

        InOrder inOrder = inOrder(
                teamFileWriteService, teamSpaceMapper, teamMemberMapper,
                teamInvitationService, shareService, teamPermissionService);
        inOrder.verify(teamFileWriteService).permanentlyDeleteTeamSpaceFiles(TEAM_ID);
        inOrder.verify(teamSpaceMapper).updateTeamStatus(TEAM_ID, TeamStatus.DISSOLVED.getValue());
        inOrder.verify(teamMemberMapper).updateActiveMembersStatusByTeam(TEAM_ID, MemberStatus.EXITED.getValue());
        inOrder.verify(teamInvitationService).markPendingAsTeamDissolved(TEAM_ID);
        inOrder.verify(shareService).invalidateTeamShares(TEAM_ID);
        inOrder.verify(teamPermissionService).reloadPermissionCache();
    }

    @Test
    @DisplayName("查询团队详情：Owner 缺失时显示未知，非成员 role 为空")
    void getTeamById_ownerMissingUsesUnknownAndRoleNull() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        TeamResponseVO response = new TeamResponseVO();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(sysUserMapper.selectById(OWNER_ID)).thenReturn(null);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ACCOUNT_ID)).thenReturn(null);
        when(teamSpaceConverter.toTeamVO(team, "未知", null)).thenReturn(response);

        TeamResponseVO result = withCurrentAccount(() -> teamService.getTeamById(TEAM_ID));

        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("查询团队详情：团队不存在抛资源不存在")
    void getTeamById_missingThrows() {
        TeamServiceImpl teamService = newTeamService();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(null);

        assertThatThrownBy(() -> teamService.getTeamById(TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("团队列表：当前账户无成员关系时直接返回空列表")
    void listUserTeams_noMembershipsReturnsEmpty() {
        TeamServiceImpl teamService = newTeamService();
        when(teamMemberMapper.selectActiveMembershipsByAccount(OWNER_ACCOUNT_ID)).thenReturn(List.of());

        List<TeamResponseVO> result = withCurrentAccount(teamService::listUserTeams);

        assertThat(result).isEmpty();
        verifyNoInteractions(teamSpaceConverter);
    }

    @Test
    @DisplayName("团队列表：成员关系存在但团队已不存在时返回空列表")
    void listUserTeams_membershipsWithoutTeamsReturnsEmpty() {
        TeamServiceImpl teamService = newTeamService();
        TeamMember owner = activeOwnerMember();
        when(teamMemberMapper.selectActiveMembershipsByAccount(OWNER_ACCOUNT_ID)).thenReturn(List.of(owner));
        when(teamSpaceMapper.selectBatchIds(any())).thenReturn(List.of());

        List<TeamResponseVO> result = withCurrentAccount(teamService::listUserTeams);

        assertThat(result).isEmpty();
        verifyNoInteractions(sysUserMapper, teamSpaceConverter);
    }

    @Test
    @DisplayName("团队列表：批量补充 Owner 昵称并保留成员角色")
    void listUserTeams_mapsOwnerNamesAndRoles() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        TeamMember member = activeOwnerMember();
        SysUser ownerUser = new SysUser();
        ownerUser.setId(OWNER_ID);
        ownerUser.setNickname("Owner");
        TeamResponseVO response = new TeamResponseVO();
        when(teamMemberMapper.selectActiveMembershipsByAccount(OWNER_ACCOUNT_ID)).thenReturn(List.of(member));
        when(teamSpaceMapper.selectBatchIds(any())).thenReturn(List.of(team));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(ownerUser));
        when(teamSpaceConverter.toTeamVO(team, "Owner", MemberRole.Owner.getValue())).thenReturn(response);

        List<TeamResponseVO> result = withCurrentAccount(teamService::listUserTeams);

        assertThat(result).containsExactly(response);
    }

    @Test
    @DisplayName("更新团队：活动团队通过 converter 更新并返回最新 VO")
    void updateTeam_activeTeamUpdatesAndReturnsVo() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        TeamUpdateRequestVO request = new TeamUpdateRequestVO();
        TeamMember owner = activeOwnerMember();
        SysUser ownerUser = new SysUser();
        ownerUser.setNickname("Owner");
        TeamResponseVO response = new TeamResponseVO();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        doAnswer(invocation -> {
            TeamSpace target = invocation.getArgument(1);
            target.setName("新团队名");
            return null;
        }).when(teamSpaceConverter).toUpdatedEntity(request, team);
        when(sysUserMapper.selectById(OWNER_ID)).thenReturn(ownerUser);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ACCOUNT_ID)).thenReturn(owner);
        when(teamSpaceConverter.toTeamVO(team, "Owner", MemberRole.Owner.getValue())).thenReturn(response);

        TeamResponseVO result = withCurrentAccount(() -> teamService.updateTeam(TEAM_ID, request));

        assertThat(result).isSameAs(response);
        verify(teamSpaceMapper).updateById(team);
        assertThat(team.getName()).isEqualTo("新团队名");
    }

    @Test
    @DisplayName("更新团队：已解散团队拒绝操作")
    void updateTeam_dissolvedTeamThrows() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        team.setStatus(TeamStatus.DISSOLVED.getValue());
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        assertThatThrownBy(() -> withCurrentAccount(() -> teamService.updateTeam(TEAM_ID, new TeamUpdateRequestVO())))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);

        verify(teamSpaceMapper, org.mockito.Mockito.never()).updateById(any());
    }

    @Test
    @DisplayName("解散团队：非 Owner 拒绝")
    void dissolveTeam_nonOwnerThrows() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        assertThatThrownBy(() -> withCurrentIdentity(OWNER_ID, 99L, () -> {
            teamService.dissolveTeam(TEAM_ID);
            return null;
        }))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);

        verifyNoInteractions(teamFileWriteService, teamInvitationService, shareService);
    }

    @Test
    @DisplayName("解散团队：状态更新 0 行时抛操作异常")
    void dissolveTeam_statusChangedThrowsOperationException() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersByTeam(TEAM_ID)).thenReturn(List.of(activeOwnerMember()));
        when(teamSpaceMapper.updateTeamStatus(TEAM_ID, TeamStatus.DISSOLVED.getValue())).thenReturn(0);

        assertThatThrownBy(() -> withCurrentIdentity(OWNER_ID, OWNER_ACCOUNT_ID, () -> {
            teamService.dissolveTeam(TEAM_ID);
            return null;
        }))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_OPERATION_EXCEPTION);

        verify(teamFileWriteService).permanentlyDeleteTeamSpaceFiles(TEAM_ID);
        verifyNoMoreInteractions(teamInvitationService, shareService, teamPermissionService);
    }

    @Test
    @DisplayName("查询团队配额：通过 converter 返回配额视图")
    void getTeamQuota_returnsQuotaVo() {
        TeamServiceImpl teamService = newTeamService();
        TeamSpace team = activeTeam();
        TeamQuotaResponseVO response = new TeamQuotaResponseVO();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(teamSpaceConverter.toQuotaVO(team)).thenReturn(response);

        assertThat(teamService.getTeamQuota(TEAM_ID)).isSameAs(response);
    }

    private TeamServiceImpl newTeamService() {
        return new TeamServiceImpl(
                teamSpaceMapper,
                teamMemberMapper,
                sysUserMapper,
                teamSpaceConverter,
                teamMemberConverter,
                teamInvitationService,
                teamPermissionService,
                teamQuotaService,
                shareService,
                teamFileWriteService);
    }

    private TeamSpace activeTeam() {
        TeamSpace team = new TeamSpace();
        team.setId(TEAM_ID);
        team.setName("测试团队");
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(OWNER_ACCOUNT_ID);
        team.setStatus(TeamStatus.ACTIVE.getValue());
        team.setTotalQuota(1024L);
        team.setUsedSpace(0L);
        return team;
    }

    private TeamMember activeOwnerMember() {
        return activeMember(OWNER_ID, MemberRole.Owner.getValue());
    }

    private TeamMember activeMember(Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(TEAM_ID);
        member.setUserId(userId);
        member.setAccountId(userId.equals(OWNER_ID) ? OWNER_ACCOUNT_ID : userId);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE.getValue());
        return member;
    }

    private <T> T withCurrentAccount(java.util.function.Supplier<T> action) {
        return withCurrentIdentity(OWNER_ID, OWNER_ACCOUNT_ID, action);
    }

    private <T> T withCurrentIdentity(Long userId, Long accountId, java.util.function.Supplier<T> action) {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(accountId);
            return action.get();
        }
    }
}
