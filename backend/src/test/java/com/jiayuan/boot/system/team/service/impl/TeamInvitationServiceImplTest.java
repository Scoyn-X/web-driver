package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.converter.TeamInvitationConverter;
import com.jiayuan.boot.system.team.mapper.TeamInvitationMapper;
import com.jiayuan.boot.system.team.mapper.TeamMemberMapper;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.bo.PendingInvitationBO;
import com.jiayuan.boot.system.team.model.entity.TeamInvitation;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.InvitationAction;
import com.jiayuan.boot.system.team.model.enums.InvitationStatus;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.enums.TeamStatus;
import com.jiayuan.boot.system.team.model.vo.InvitationActionRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionResponseVO;
import com.jiayuan.boot.system.team.model.vo.InvitationResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.workflow.service.TeamInvitationWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团队邀请服务测试
 *
 * @author charleslam
 * @since 2026/05/20
 */
@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceImplTest {

    private static final Long INVITATION_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long INVITER_ID = 20L;
    private static final Long INVITEE_ID = 30L;
    private static final Long INVITER_ACCOUNT_ID = 200L;
    private static final Long INVITEE_ACCOUNT_ID = 300L;
    private static final Long OTHER_INVITEE_ACCOUNT_ID = 301L;
    private static final String EDITOR_ROLE = MemberRole.Editor.getValue();
    private static final String PROCESS_INSTANCE_ID = "process-1";
    private static final String OTHER_PROCESS_INSTANCE_ID = "other-process";
    private static final String REJECT_REASON = "暂不加入";

    @Mock
    private TeamInvitationMapper teamInvitationMapper;

    @Mock
    private TeamMemberMapper teamMemberMapper;

    @Mock
    private TeamSpaceMapper teamSpaceMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private TeamPermissionService teamPermissionService;

    @Mock
    private TeamInvitationWorkflowService workflowService;

    @Mock
    private TeamInvitationConverter teamInvitationConverter;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private TeamInvitationServiceImpl invitationService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void inviteShouldCreatePendingInvitationAndStartBpmnProcess() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITER_ACCOUNT_ID);
        InvitationActionRequestVO request = inviteRequest();
        TeamInvitation invitation = pendingInvitation();
        InvitationActionResponseVO responseVO = actionResponse(InvitationStatus.PENDING);

        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(sysUserMapper.selectUserBriefByAccountId(INVITEE_ACCOUNT_ID)).thenReturn(user(INVITEE_ID, INVITEE_ACCOUNT_ID));
        stubInviteCreation(invitation);
        stubActionResponse(InvitationAction.INVITE, invitation, responseVO);

        InvitationActionResponseVO response = invitationService.handleInvitationAction(TEAM_ID, request);

        assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING.getValue());
        ArgumentCaptor<PendingInvitationBO> invitationCaptor = ArgumentCaptor.forClass(PendingInvitationBO.class);
        verify(teamInvitationConverter).toPendingInvitation(invitationCaptor.capture());
        assertThat(invitationCaptor.getValue().getTeamId()).isEqualTo(TEAM_ID);
        assertThat(invitationCaptor.getValue().getInviterId()).isEqualTo(INVITER_ID);
        assertThat(invitationCaptor.getValue().getInviterAccountId()).isEqualTo(INVITER_ACCOUNT_ID);
        assertThat(invitationCaptor.getValue().getInviteeId()).isEqualTo(INVITEE_ID);
        assertThat(invitationCaptor.getValue().getInviteeAccountId()).isEqualTo(INVITEE_ACCOUNT_ID);
        assertThat(invitationCaptor.getValue().getTargetRole()).isEqualTo(EDITOR_ROLE);
        verify(teamInvitationMapper).insert(invitation);
        verify(teamInvitationMapper).updateFlowableInstanceId(INVITATION_ID, PROCESS_INSTANCE_ID);
        verify(workflowService).startInvitation(
                INVITATION_ID, TEAM_ID, INVITER_ACCOUNT_ID, INVITEE_ACCOUNT_ID, EDITOR_ROLE, 86400L);
    }

    @Test
    void inviteShouldResolveLegacyUserIdToPrimaryAccount() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITER_ACCOUNT_ID);
        InvitationActionRequestVO request = inviteRequest();
        request.setInviteeAccountId(null);
        TeamInvitation invitation = pendingInvitation();
        InvitationActionResponseVO responseVO = actionResponse(InvitationStatus.PENDING);

        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(sysUserMapper.selectUserBriefById(INVITEE_ID)).thenReturn(user(INVITEE_ID, INVITEE_ACCOUNT_ID));
        stubInviteCreation(invitation);
        stubActionResponse(InvitationAction.INVITE, invitation, responseVO);

        invitationService.handleInvitationAction(TEAM_ID, request);

        verify(sysUserMapper).selectUserBriefById(INVITEE_ID);
        verify(workflowService).startInvitation(
                INVITATION_ID, TEAM_ID, INVITER_ACCOUNT_ID, INVITEE_ACCOUNT_ID, EDITOR_ROLE, 86400L);
    }

    @Test
    void acceptShouldCreateMemberAndCompleteBpmnTask() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITEE_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITEE_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();
        TeamMember member = activeMember();
        TeamMemberResponseVO memberVO = memberResponse();
        InvitationActionResponseVO responseVO = actionResponse(InvitationStatus.ACCEPTED);
        responseVO.setMember(memberVO);

        stubAcceptSuccess(invitation, member, member, memberVO, responseVO, 1);

        InvitationActionResponseVO response = invitationService.handleInvitationAction(
                TEAM_ID, actionRequest(InvitationAction.ACCEPT));

        assertThat(response.getStatus()).isEqualTo(InvitationStatus.ACCEPTED.getValue());
        assertThat(response.getMember()).isSameAs(memberVO);
        verify(teamMemberMapper).upsertAcceptedMember(member);
        verify(teamMemberMapper).selectActiveMemberByAccount(TEAM_ID, INVITEE_ACCOUNT_ID);
        verify(teamInvitationMapper).updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.ACCEPTED.getValue(), null);
        verify(workflowService).completeInvitation(PROCESS_INSTANCE_ID, InvitationAction.ACCEPT.getValue());
        verify(teamPermissionService).reloadPermissionCache();
    }

    @Test
    void acceptShouldRestoreHistoricalInactiveMember() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITEE_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITEE_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();
        TeamMember member = activeMember();
        TeamMember restoredMember = activeMember();
        restoredMember.setId(99L);
        TeamMemberResponseVO memberVO = memberResponse();
        InvitationActionResponseVO responseVO = actionResponse(InvitationStatus.ACCEPTED);
        responseVO.setMember(memberVO);

        stubAcceptSuccess(invitation, member, restoredMember, memberVO, responseVO, 2);

        InvitationActionResponseVO response = invitationService.handleInvitationAction(
                TEAM_ID, actionRequest(InvitationAction.ACCEPT));

        assertThat(response.getStatus()).isEqualTo(InvitationStatus.ACCEPTED.getValue());
        verify(teamMemberMapper).upsertAcceptedMember(member);
        verify(teamInvitationConverter).toMemberResponseVO(eq(restoredMember), any());
    }

    @Test
    void acceptShouldRejectOtherAccountUnderSameUser() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITEE_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(OTHER_INVITEE_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(invitation);

        assertThatThrownBy(() -> invitationService.handleInvitationAction(
                TEAM_ID, actionRequest(InvitationAction.ACCEPT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能处理发给当前账户的邀请");

        verify(teamMemberMapper, never()).upsertAcceptedMember(any());
    }

    @Test
    void rejectShouldCompleteBpmnTaskWithoutCreatingMember() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITEE_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITEE_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();
        InvitationActionRequestVO request = actionRequest(InvitationAction.REJECT);
        request.setReason(REJECT_REASON);

        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(invitation);
        when(teamInvitationMapper.updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(),
                InvitationStatus.REJECTED.getValue(), REJECT_REASON))
                .thenReturn(1);
        stubActionResponse(InvitationAction.REJECT, invitation, actionResponse(InvitationStatus.REJECTED));

        invitationService.handleInvitationAction(TEAM_ID, request);

        verify(teamInvitationMapper).updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(),
                InvitationStatus.REJECTED.getValue(), REJECT_REASON);
        verify(workflowService).completeInvitation(PROCESS_INSTANCE_ID, InvitationAction.REJECT.getValue());
        verify(teamMemberMapper, never()).upsertAcceptedMember(any());
    }

    @Test
    void listTeamInvitationsShouldExpireStalePendingBeforeQuery() {
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectTeamInvitationResponses(
                TEAM_ID, InvitationStatus.PENDING.getValue())).thenReturn(List.of());

        invitationService.listTeamInvitations(TEAM_ID, InvitationStatus.PENDING.getValue());

        verify(teamInvitationMapper).expirePendingInvitationsByTeam(
                eq(TEAM_ID),
                eq(InvitationStatus.PENDING.getValue()),
                eq(InvitationStatus.EXPIRED.getValue()),
                eq(InvitationStatus.EXPIRED.getDefaultReason()),
                any());
        verify(teamInvitationMapper).selectTeamInvitationResponses(
                TEAM_ID, InvitationStatus.PENDING.getValue());
    }

    @Test
    void listMyInvitationsShouldExpireStalePendingBeforeQuery() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITEE_ACCOUNT_ID);
        when(teamInvitationMapper.selectMyInvitationResponses(
                INVITEE_ACCOUNT_ID, InvitationStatus.PENDING.getValue())).thenReturn(List.of());

        invitationService.listMyInvitations(InvitationStatus.PENDING.getValue());

        verify(teamInvitationMapper).expirePendingInvitationsByInviteeAccount(
                eq(INVITEE_ACCOUNT_ID),
                eq(InvitationStatus.PENDING.getValue()),
                eq(InvitationStatus.EXPIRED.getValue()),
                eq(InvitationStatus.EXPIRED.getDefaultReason()),
                any());
        verify(teamInvitationMapper).selectMyInvitationResponses(
                INVITEE_ACCOUNT_ID, InvitationStatus.PENDING.getValue());
    }

    @Test
    void revokeShouldCancelBpmnProcess() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITER_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();

        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(invitation);
        when(teamInvitationMapper.updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.REVOKED.getValue(),
                InvitationAction.REVOKE.getDefaultReason()))
                .thenReturn(1);
        stubActionResponse(InvitationAction.REVOKE, invitation, actionResponse(InvitationStatus.REVOKED));

        invitationService.handleInvitationAction(TEAM_ID, actionRequest(InvitationAction.REVOKE));

        verify(teamInvitationMapper).updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.REVOKED.getValue(),
                InvitationAction.REVOKE.getDefaultReason());
        verify(workflowService).cancelInvitation(PROCESS_INSTANCE_ID, InvitationAction.REVOKE.getDefaultReason());
    }

    @Test
    void rejectShouldRequireInvitee() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITER_ACCOUNT_ID);
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(pendingInvitation());

        assertThatThrownBy(() -> invitationService.handleInvitationAction(
                TEAM_ID, actionRequest(InvitationAction.REJECT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能处理发给当前账户的邀请");
    }

    @Test
    void actionShouldMarkInvitationExpiredWhenTimerLags() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(INVITEE_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITEE_ACCOUNT_ID);
        TeamInvitation invitation = pendingInvitation();
        invitation.setExpireAt(LocalDateTime.now().minusMinutes(1));

        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(invitation);

        assertThatThrownBy(() -> invitationService.handleInvitationAction(
                TEAM_ID, actionRequest(InvitationAction.ACCEPT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邀请已过期");
        ArgumentCaptor<TransactionDefinition> transactionCaptor =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(teamInvitationMapper).updatePendingInvitationStatus(
                INVITATION_ID,
                InvitationStatus.PENDING.getValue(),
                InvitationStatus.EXPIRED.getValue(),
                InvitationStatus.EXPIRED.getDefaultReason());
    }

    @Test
    void expireInvitationShouldOnlyMarkMatchingPendingInvitationExpired() {
        TeamInvitation invitation = pendingInvitation();
        when(teamInvitationMapper.selectById(INVITATION_ID)).thenReturn(invitation);
        when(teamInvitationMapper.updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.EXPIRED.getValue(),
                InvitationStatus.EXPIRED.getDefaultReason()))
                .thenReturn(1);

        invitationService.expireInvitation(INVITATION_ID, PROCESS_INSTANCE_ID);

        verify(teamInvitationMapper).updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.EXPIRED.getValue(),
                InvitationStatus.EXPIRED.getDefaultReason());
    }

    @Test
    void expireInvitationShouldSkipWhenProcessDoesNotMatch() {
        when(teamInvitationMapper.selectById(INVITATION_ID)).thenReturn(pendingInvitation());

        invitationService.expireInvitation(INVITATION_ID, OTHER_PROCESS_INSTANCE_ID);

        verify(teamInvitationMapper, never()).updatePendingInvitationStatus(
                anyLong(), anyString(), anyString(), any());
    }

    @Test
    void expireInvitationShouldSkipWhenInvitationIsNotPending() {
        TeamInvitation invitation = pendingInvitation();
        invitation.setStatus(InvitationStatus.ACCEPTED.getValue());
        when(teamInvitationMapper.selectById(INVITATION_ID)).thenReturn(invitation);

        invitationService.expireInvitation(INVITATION_ID, PROCESS_INSTANCE_ID);

        verify(teamInvitationMapper, never()).updatePendingInvitationStatus(
                anyLong(), anyString(), anyString(), any());
    }

    @Test
    void getProcessDiagramShouldAllowInvitationParticipantAccount() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(INVITER_ACCOUNT_ID);
        when(teamInvitationMapper.selectById(INVITATION_ID)).thenReturn(pendingInvitation());

        invitationService.getProcessDiagram(INVITATION_ID);

        verify(workflowService).getProcessDiagram(PROCESS_INSTANCE_ID);
    }

    @Test
    void getProcessDiagramShouldRejectUnrelatedAccount() {
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(999L);
        when(teamInvitationMapper.selectById(INVITATION_ID)).thenReturn(pendingInvitation());

        assertThatThrownBy(() -> invitationService.getProcessDiagram(INVITATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权查看该邀请流程");
        verify(workflowService, never()).getProcessDiagram(anyString());
    }

    private void stubActionResponse(InvitationAction action, TeamInvitation invitation,
                                    InvitationActionResponseVO responseVO) {
        stubActionResponse(action, invitation, null, responseVO);
    }

    private void stubInviteCreation(TeamInvitation invitation) {
        when(teamInvitationMapper.existsActiveMemberByAccount(
                TEAM_ID, INVITEE_ACCOUNT_ID, MemberStatus.ACTIVE.getValue()))
                .thenReturn(false);
        when(teamInvitationMapper.existsActivePendingInvitation(
                eq(TEAM_ID), eq(INVITEE_ACCOUNT_ID), eq(InvitationStatus.PENDING.getValue()), any()))
                .thenReturn(false);
        when(teamInvitationConverter.toPendingInvitation(any(PendingInvitationBO.class))).thenReturn(invitation);
        when(workflowService.startInvitation(
                INVITATION_ID, TEAM_ID, INVITER_ACCOUNT_ID, INVITEE_ACCOUNT_ID, EDITOR_ROLE, 86400L))
                .thenReturn(PROCESS_INSTANCE_ID);
    }

    private void stubAcceptSuccess(TeamInvitation invitation, TeamMember member, TeamMember selectedMember,
                                   TeamMemberResponseVO memberVO, InvitationActionResponseVO responseVO,
                                   int affectedRows) {
        when(teamSpaceMapper.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamInvitationMapper.selectPendingInvitation(
                TEAM_ID, INVITATION_ID, InvitationStatus.PENDING.getValue())).thenReturn(invitation);
        when(teamInvitationMapper.existsActiveMemberByAccount(
                TEAM_ID, INVITEE_ACCOUNT_ID, MemberStatus.ACTIVE.getValue()))
                .thenReturn(false);
        when(teamInvitationConverter.toActiveMember(eq(invitation), any())).thenReturn(member);
        when(teamMemberMapper.upsertAcceptedMember(member)).thenReturn(affectedRows);
        when(teamMemberMapper.selectActiveMemberByAccount(TEAM_ID, INVITEE_ACCOUNT_ID)).thenReturn(selectedMember);
        when(sysUserMapper.selectUserBriefByAccountId(INVITEE_ACCOUNT_ID))
                .thenReturn(user(INVITEE_ID, INVITEE_ACCOUNT_ID));
        when(teamInvitationMapper.updatePendingInvitationStatus(
                INVITATION_ID, InvitationStatus.PENDING.getValue(), InvitationStatus.ACCEPTED.getValue(), null))
                .thenReturn(1);
        when(teamInvitationConverter.toMemberResponseVO(eq(selectedMember), any())).thenReturn(memberVO);
        stubActionResponse(InvitationAction.ACCEPT, invitation, memberVO, responseVO);
    }

    private void stubActionResponse(InvitationAction action, TeamInvitation invitation,
                                    TeamMemberResponseVO memberVO, InvitationActionResponseVO responseVO) {
        InvitationResponseVO invitationVO = new InvitationResponseVO();
        invitationVO.setStatus(responseVO.getStatus());
        when(teamInvitationMapper.selectInvitationResponseById(invitation.getId())).thenReturn(invitationVO);
        when(teamInvitationConverter.toActionResponseVO(
                eq(action.getValue()), eq(invitationVO), eq(memberVO), eq(action.getSuccessMessage())))
                .thenReturn(responseVO);
    }

    private InvitationActionRequestVO inviteRequest() {
        InvitationActionRequestVO request = new InvitationActionRequestVO();
        request.setAction(InvitationAction.INVITE.getValue());
        request.setInviteeUserId(INVITEE_ID);
        request.setInviteeAccountId(INVITEE_ACCOUNT_ID);
        request.setRoleCode(EDITOR_ROLE);
        return request;
    }

    private InvitationActionRequestVO actionRequest(InvitationAction action) {
        InvitationActionRequestVO request = new InvitationActionRequestVO();
        request.setAction(action.getValue());
        request.setInvitationId(INVITATION_ID);
        return request;
    }

    private InvitationActionResponseVO actionResponse(InvitationStatus status) {
        InvitationActionResponseVO response = new InvitationActionResponseVO();
        response.setStatus(status.getValue());
        return response;
    }

    private TeamInvitation pendingInvitation() {
        TeamInvitation invitation = new TeamInvitation();
        invitation.setId(INVITATION_ID);
        invitation.setTeamId(TEAM_ID);
        invitation.setInviterId(INVITER_ID);
        invitation.setInviterAccountId(INVITER_ACCOUNT_ID);
        invitation.setInviteeId(INVITEE_ID);
        invitation.setInviteeAccountId(INVITEE_ACCOUNT_ID);
        invitation.setTargetRole(EDITOR_ROLE);
        invitation.setStatus(InvitationStatus.PENDING.getValue());
        invitation.setExpireAt(LocalDateTime.now().plusHours(24));
        invitation.setFlowableInstanceId(PROCESS_INSTANCE_ID);
        return invitation;
    }

    private TeamMember activeMember() {
        TeamMember member = new TeamMember();
        member.setId(2L);
        member.setTeamId(TEAM_ID);
        member.setUserId(INVITEE_ID);
        member.setAccountId(INVITEE_ACCOUNT_ID);
        member.setRole(EDITOR_ROLE);
        member.setStatus(MemberStatus.ACTIVE.getValue());
        return member;
    }

    private TeamMemberResponseVO memberResponse() {
        TeamMemberResponseVO member = new TeamMemberResponseVO();
        member.setId(2L);
        member.setUserId(INVITEE_ID);
        member.setAccountId(INVITEE_ACCOUNT_ID);
        member.setRole(EDITOR_ROLE);
        member.setStatus(MemberStatus.ACTIVE.getValue());
        return member;
    }

    private TeamSpace activeTeam() {
        TeamSpace team = new TeamSpace();
        team.setId(TEAM_ID);
        team.setName("Lab3");
        team.setStatus(TeamStatus.ACTIVE.getValue());
        return team;
    }

    private UserBriefBO user(Long userId, Long accountId) {
        UserBriefBO user = new UserBriefBO();
        user.setUserId(userId);
        user.setAccountId(accountId);
        user.setNickname("user-" + userId);
        return user;
    }
}
