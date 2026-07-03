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
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberServiceImpl 单元测试")
class MemberServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long EDITOR_ID = 3L;
    private static final Long VIEWER_ID = 4L;
    private static final Long TEAM_ID = 1L;

    @Mock private TeamSpaceMapper teamSpaceMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysAccountMapper sysAccountMapper;
    @Mock private TeamMemberConverter teamMemberConverter;
    @Mock private TeamPermissionService teamPermissionService;
    @Mock private TeamQuotaService teamQuotaService;
    @Mock private ShareService shareService;

    @InjectMocks
    private MemberServiceImpl memberService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== removeMember ====================

    @Test
    @DisplayName("Admin 移除普通成员成功")
    void removeMember_adminRemovesEditor() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember target = member(EDITOR_ID, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectById(EDITOR_ID)).thenReturn(target);
        when(teamMemberMapper.updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.REMOVED.getValue()))
                .thenReturn(1);

        memberService.removeMember(TEAM_ID, EDITOR_ID);

        verify(teamMemberMapper).updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.REMOVED.getValue());
        verify(shareService).invalidateTeamSharesByCreator(TEAM_ID, EDITOR_ID);
    }

    @Test
    @DisplayName("不能移除 Owner")
    void removeMember_cannotRemoveOwner() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember owner = member(OWNER_ID, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectById(OWNER_ID)).thenReturn(owner);

        assertThatThrownBy(() -> memberService.removeMember(TEAM_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    @Test
    @DisplayName("成员被移除后无法再通过权限校验")
    void removeMember_permissionCacheReloaded() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember target = member(EDITOR_ID, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectById(EDITOR_ID)).thenReturn(target);
        when(teamMemberMapper.updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.REMOVED.getValue()))
                .thenReturn(1);

        memberService.removeMember(TEAM_ID, EDITOR_ID);

        verify(teamPermissionService).reloadPermissionCache();
    }

    @Test
    @DisplayName("移除成员按成员ID定位并按账户ID失效分享")
    void removeMember_usesMemberIdAndInvalidatesByAccountId() {
        Long targetMemberId = 88L;
        Long targetUserId = 3L;
        Long targetAccountId = 303L;
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamMemberMapper.selectById(targetMemberId))
                .thenReturn(member(targetMemberId, targetUserId, targetAccountId, MemberRole.Editor.getValue()));
        when(teamMemberMapper.updateActiveMemberStatus(TEAM_ID, targetAccountId, MemberStatus.REMOVED.getValue()))
                .thenReturn(1);

        memberService.removeMember(TEAM_ID, targetMemberId);

        verify(teamMemberMapper).updateActiveMemberStatus(TEAM_ID, targetAccountId, MemberStatus.REMOVED.getValue());
        verify(shareService).invalidateTeamSharesByCreator(TEAM_ID, targetAccountId);
    }

    // ==================== exitTeam ====================

    @Test
    @DisplayName("普通成员退出团队成功")
    void exitTeam_editorExits() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(EDITOR_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember self = member(EDITOR_ID, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, EDITOR_ID)).thenReturn(self);
        when(teamMemberMapper.updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.EXITED.getValue()))
                .thenReturn(1);

        memberService.exitTeam(TEAM_ID);

        verify(teamMemberMapper).updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.EXITED.getValue());
        verify(shareService).invalidateTeamSharesByCreator(TEAM_ID, EDITOR_ID);
    }

    @Test
    @DisplayName("Owner 不能直接退出团队")
    void exitTeam_ownerCannotExit() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember self = member(OWNER_ID, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ID)).thenReturn(self);

        assertThatThrownBy(() -> memberService.exitTeam(TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    @Test
    @DisplayName("退出后权限缓存刷新")
    void exitTeam_permissionCacheReloaded() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(EDITOR_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember self = member(EDITOR_ID, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, EDITOR_ID)).thenReturn(self);
        when(teamMemberMapper.updateActiveMemberStatus(TEAM_ID, EDITOR_ID, MemberStatus.EXITED.getValue()))
                .thenReturn(1);

        memberService.exitTeam(TEAM_ID);

        verify(teamPermissionService).reloadPermissionCache();
    }

    // ==================== updateMemberRole ====================

    @Test
    @DisplayName("Admin 修改 Editor 角色为 Viewer 成功")
    void updateMemberRole_adminChangesEditorRole() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember target = member(EDITOR_ID, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectById(EDITOR_ID)).thenReturn(target);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, EDITOR_ID, MemberRole.Viewer.name())).thenReturn(1);
        TeamMemberResponseVO vo = memberVO(EDITOR_ID, MemberRole.Viewer.name());
        when(teamMemberConverter.toMemberVO(any(TeamMemberDisplayBO.class))).thenReturn(vo);
        when(sysUserMapper.selectUserBriefById(EDITOR_ID))
                .thenReturn(userBrief(EDITOR_ID, "EditorUser", "editor@test.com"));
        when(sysAccountMapper.selectById(EDITOR_ID)).thenReturn(account(EDITOR_ID, "editor-account"));

        MemberRoleUpdateRequestVO request = new MemberRoleUpdateRequestVO();
        request.setRole(MemberRole.Viewer.name());

        TeamMemberResponseVO result = memberService.updateMemberRole(TEAM_ID, EDITOR_ID, request);

        verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, EDITOR_ID, MemberRole.Viewer.name());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("不能将成员角色改为 Owner")
    void updateMemberRole_cannotSetOwner() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());

        MemberRoleUpdateRequestVO request = new MemberRoleUpdateRequestVO();
        request.setRole(MemberRole.Owner.name());

        assertThatThrownBy(() -> memberService.updateMemberRole(TEAM_ID, EDITOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("不能修改 Owner 的角色")
    void updateMemberRole_cannotChangeOwnerRole() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember owner = member(OWNER_ID, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectById(OWNER_ID)).thenReturn(owner);

        MemberRoleUpdateRequestVO request = new MemberRoleUpdateRequestVO();
        request.setRole(MemberRole.Editor.name());

        assertThatThrownBy(() -> memberService.updateMemberRole(TEAM_ID, OWNER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    @Test
    @DisplayName("修改成员角色按成员ID定位并按账户ID更新")
    void updateMemberRole_usesMemberIdAndUpdatesAccountRole() {
        Long targetMemberId = 89L;
        Long targetUserId = 3L;
        Long targetAccountId = 303L;
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        TeamMember target = member(targetMemberId, targetUserId, targetAccountId, MemberRole.Editor.getValue());
        when(teamMemberMapper.selectById(targetMemberId)).thenReturn(target);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, targetAccountId, MemberRole.Viewer.name()))
                .thenReturn(1);
        when(sysUserMapper.selectUserBriefById(targetUserId))
                .thenReturn(userBrief(targetUserId, targetAccountId, "EditorUser", "editor@test.com"));
        when(sysAccountMapper.selectById(targetAccountId)).thenReturn(account(targetAccountId, "editor-work"));
        TeamMemberResponseVO vo = memberVO(targetUserId, MemberRole.Viewer.name());
        when(teamMemberConverter.toMemberVO(any(TeamMemberDisplayBO.class))).thenReturn(vo);

        MemberRoleUpdateRequestVO request = new MemberRoleUpdateRequestVO();
        request.setRole(MemberRole.Viewer.name());

        memberService.updateMemberRole(TEAM_ID, targetMemberId, request);

        verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, targetAccountId, MemberRole.Viewer.name());
        ArgumentCaptor<TeamMemberDisplayBO> displayCaptor = ArgumentCaptor.forClass(TeamMemberDisplayBO.class);
        verify(teamMemberConverter).toMemberVO(displayCaptor.capture());
        assertThat(displayCaptor.getValue().getMember()).isSameAs(target);
        assertThat(displayCaptor.getValue().getRole()).isEqualTo(MemberRole.Viewer.name());
        assertThat(displayCaptor.getValue().getAccountName()).isEqualTo("editor-work");
    }

    // ==================== transferOwner ====================

    @Test
    @DisplayName("Owner 转让所有权给 Admin 成功")
    void transferOwner_success() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ID);
        TeamSpace team = activeTeam();
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(OWNER_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        TeamMember target = member(ADMIN_ID, MemberRole.Admin.getValue());
        TeamMember self = member(OWNER_ID, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectById(ADMIN_ID)).thenReturn(target);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ID)).thenReturn(self);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, ADMIN_ID, MemberRole.Owner.getValue()))
                .thenReturn(1);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, OWNER_ID, MemberRole.Admin.getValue()))
                .thenReturn(1);
        when(teamSpaceMapper.updateOwner(TEAM_ID, ADMIN_ID, ADMIN_ID)).thenReturn(1);

        TransferOwnerRequestVO request = new TransferOwnerRequestVO();
        request.setTargetMemberId(ADMIN_ID);

        memberService.transferOwner(TEAM_ID, request);

        InOrder inOrder = inOrder(teamMemberMapper, teamSpaceMapper, teamQuotaService, teamPermissionService);
        inOrder.verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, ADMIN_ID, MemberRole.Owner.getValue());
        inOrder.verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, OWNER_ID, MemberRole.Admin.getValue());
        inOrder.verify(teamSpaceMapper).updateOwner(TEAM_ID, ADMIN_ID, ADMIN_ID);
        inOrder.verify(teamQuotaService).syncTeamTotalQuotaByOwner(TEAM_ID, ADMIN_ID);
        inOrder.verify(teamPermissionService).reloadPermissionCache();
    }

    @Test
    @DisplayName("Owner 转让所有权使用目标账户ID")
    void transferOwner_usesTargetAccountIdWhenUserDiffers() {
        Long ownerAccountId = 101L;
        Long targetMemberId = 88L;
        Long targetUserId = 3L;
        Long targetAccountId = 303L;
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ownerAccountId);
        TeamSpace team = activeTeam();
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(ownerAccountId);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        TeamMember target = member(targetMemberId, targetUserId, targetAccountId, MemberRole.Admin.getValue());
        TeamMember self = member(77L, OWNER_ID, ownerAccountId, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, targetAccountId)).thenReturn(target);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, ownerAccountId)).thenReturn(self);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, targetAccountId, MemberRole.Owner.getValue()))
                .thenReturn(1);
        when(teamMemberMapper.updateActiveMemberRole(TEAM_ID, ownerAccountId, MemberRole.Admin.getValue()))
                .thenReturn(1);
        when(teamSpaceMapper.updateOwner(TEAM_ID, targetUserId, targetAccountId)).thenReturn(1);

        TransferOwnerRequestVO request = new TransferOwnerRequestVO();
        request.setTargetAccountId(targetAccountId);

        memberService.transferOwner(TEAM_ID, request);

        verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, targetAccountId, MemberRole.Owner.getValue());
        verify(teamMemberMapper).updateActiveMemberRole(TEAM_ID, ownerAccountId, MemberRole.Admin.getValue());
        verify(teamSpaceMapper).updateOwner(TEAM_ID, targetUserId, targetAccountId);
        verify(teamQuotaService).syncTeamTotalQuotaByOwner(TEAM_ID, targetUserId);
    }

    @Test
    @DisplayName("Owner 不能将所有权转让给自己")
    void transferOwner_cannotTransferToSelf() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OWNER_ID);
        TeamSpace team = activeTeam();
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(OWNER_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, MemberRole.Owner.getValue()));

        TransferOwnerRequestVO request = new TransferOwnerRequestVO();
        request.setTargetAccountId(OWNER_ID);

        assertThatThrownBy(() -> memberService.transferOwner(TEAM_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        verify(teamMemberMapper, never()).updateActiveMemberRole(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Admin 不能转让所有权")
    void transferOwner_adminCannotTransfer() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        TeamSpace team = activeTeam();
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(OWNER_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        TransferOwnerRequestVO request = new TransferOwnerRequestVO();
        request.setTargetMemberId(ADMIN_ID);

        assertThatThrownBy(() -> memberService.transferOwner(TEAM_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    // ==================== listMembers ====================

    @Test
    @DisplayName("列出团队成员成功")
    void listMembers_success() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());

        TeamMember owner = member(OWNER_ID, MemberRole.Owner.getValue());
        TeamMember admin = member(ADMIN_ID, MemberRole.Admin.getValue());
        when(teamMemberMapper.selectActiveMembersByTeam(TEAM_ID)).thenReturn(List.of(owner, admin));

        when(sysUserMapper.selectUserBriefByIds(any())).thenReturn(List.of(
                userBrief(OWNER_ID, "OwnerUser", "owner@test.com"),
                userBrief(ADMIN_ID, "AdminUser", "admin@test.com")
        ));
        when(sysAccountMapper.selectBatchIds(any())).thenReturn(List.of(
                account(OWNER_ID, "owner-account"),
                account(ADMIN_ID, "admin-account")
        ));

        TeamMemberResponseVO ownerVO = memberVO(OWNER_ID, MemberRole.Owner.getValue());
        TeamMemberResponseVO adminVO = memberVO(ADMIN_ID, MemberRole.Admin.getValue());
        when(teamMemberConverter.toMemberVO(any(TeamMemberDisplayBO.class))).thenReturn(ownerVO, adminVO);

        List<TeamMemberResponseVO> result = memberService.listMembers(TEAM_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("团队无成员时返回空列表")
    void listMembers_empty() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        TeamSpace team = activeTeam();
        team.setOwnerId(null);
        team.setOwnerAccountId(null);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersByTeam(TEAM_ID)).thenReturn(Collections.emptyList());

        List<TeamMemberResponseVO> result = memberService.listMembers(TEAM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("成员列表按账户ID合成 Owner")
    void listMembers_synthesizesOwnerByAccountIdWhenSameUserOtherAccountExists() {
        Long ownerUserId = OWNER_ID;
        Long ownerAccountId = 101L;
        Long otherAccountId = 102L;
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        TeamSpace team = activeTeam();
        team.setOwnerId(ownerUserId);
        team.setOwnerAccountId(ownerAccountId);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(team);

        TeamMember otherAccountMember = member(88L, ownerUserId, otherAccountId, MemberRole.Admin.getValue());
        TeamMember ownerMember = member(99L, ownerUserId, ownerAccountId, MemberRole.Owner.getValue());
        when(teamMemberMapper.selectActiveMembersByTeam(TEAM_ID)).thenReturn(List.of(otherAccountMember));
        when(sysUserMapper.selectUserBriefByIds(any())).thenReturn(List.of(
                userBrief(ownerUserId, ownerAccountId, "OwnerUser", "owner@test.com")
        ));
        when(sysAccountMapper.selectBatchIds(any())).thenReturn(List.of(
                account(ownerAccountId, "owner-account"),
                account(otherAccountId, "other-account")
        ));
        when(teamMemberConverter.toOwnerMember(eq(TEAM_ID), eq(ownerUserId), eq(ownerAccountId), isNull()))
                .thenReturn(ownerMember);
        when(teamMemberConverter.toMemberVO(any(TeamMemberDisplayBO.class)))
                .thenReturn(memberVO(ownerUserId, MemberRole.Owner.getValue()),
                        memberVO(ownerUserId, MemberRole.Admin.getValue()));

        List<TeamMemberResponseVO> result = memberService.listMembers(TEAM_ID);

        assertThat(result).hasSize(2);
        ArgumentCaptor<TeamMemberDisplayBO> displayCaptor = ArgumentCaptor.forClass(TeamMemberDisplayBO.class);
        verify(teamMemberConverter, org.mockito.Mockito.times(2)).toMemberVO(displayCaptor.capture());
        assertThat(displayCaptor.getAllValues().get(0).getMember().getAccountId()).isEqualTo(ownerAccountId);
        assertThat(displayCaptor.getAllValues().get(1).getMember().getAccountId()).isEqualTo(otherAccountId);
    }

    // ==================== 团队解散后操作 ====================

    @Test
    @DisplayName("团队解散后无法移除成员")
    void teamDissolved_cannotRemoveMember() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ADMIN_ID);
        TeamSpace dissolved = new TeamSpace();
        dissolved.setId(TEAM_ID);
        dissolved.setStatus(TeamStatus.DISSOLVED.getValue());
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(dissolved);

        assertThatThrownBy(() -> memberService.removeMember(TEAM_ID, EDITOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    @Test
    @DisplayName("团队解散后无法退出")
    void teamDissolved_cannotExit() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(EDITOR_ID);
        TeamSpace dissolved = new TeamSpace();
        dissolved.setId(TEAM_ID);
        dissolved.setStatus(TeamStatus.DISSOLVED.getValue());
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(dissolved);

        assertThatThrownBy(() -> memberService.exitTeam(TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_PERMISSION_EXCEPTION);
    }

    // ==================== helpers ====================

    private TeamSpace activeTeam() {
        TeamSpace team = new TeamSpace();
        team.setId(TEAM_ID);
        team.setStatus(TeamStatus.ACTIVE.getValue());
        team.setOwnerId(OWNER_ID);
        team.setOwnerAccountId(OWNER_ID);
        team.setTotalQuota(1073741824L);
        team.setUsedSpace(0L);
        return team;
    }

    private TeamMember member(Long userId, String role) {
        return member(userId, userId, userId, role);
    }

    private TeamMember member(Long memberId, Long userId, Long accountId, String role) {
        TeamMember m = new TeamMember();
        m.setId(memberId);
        m.setTeamId(TEAM_ID);
        m.setUserId(userId);
        m.setAccountId(accountId);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE.getValue());
        return m;
    }

    private UserBriefBO userBrief(Long id, String nickname, String email) {
        return userBrief(id, id, nickname, email);
    }

    private UserBriefBO userBrief(Long userId, Long accountId, String nickname, String email) {
        UserBriefBO b = new UserBriefBO();
        b.setUserId(userId);
        b.setAccountId(accountId);
        b.setNickname(nickname);
        b.setAccountName(nickname);
        b.setEmail(email);
        return b;
    }

    private SysAccount account(Long id, String accountName) {
        SysAccount account = new SysAccount();
        account.setId(id);
        account.setAccountName(accountName);
        return account;
    }

    private TeamMemberResponseVO memberVO(Long userId, String role) {
        TeamMemberResponseVO vo = new TeamMemberResponseVO();
        vo.setUserId(userId);
        vo.setRole(role);
        return vo;
    }
}
