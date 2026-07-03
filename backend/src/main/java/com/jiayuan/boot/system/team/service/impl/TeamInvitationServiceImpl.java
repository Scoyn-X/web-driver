package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
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
import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;
import com.jiayuan.boot.system.team.service.TeamInvitationService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.team.util.TeamInvitationUtils;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.workflow.service.TeamInvitationExpireHandler;
import com.jiayuan.boot.system.workflow.service.TeamInvitationWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队邀请服务实现。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamInvitationServiceImpl implements TeamInvitationService, TeamInvitationExpireHandler {

    private static final long INVITATION_EXPIRE_SECONDS = 86400L;

    private final TeamInvitationMapper teamInvitationMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamSpaceMapper teamSpaceMapper;
    private final SysUserMapper sysUserMapper;
    private final TeamPermissionService teamPermissionService;
    private final TeamInvitationWorkflowService workflowService;
    private final TeamInvitationConverter teamInvitationConverter;
    private final PlatformTransactionManager transactionManager;

    // 查询团队邀请列表。
    @Override
    public List<InvitationResponseVO> listTeamInvitations(Long teamId, String status) {
        ensureActiveTeam(teamId);
        expirePendingInvitationsByTeam(teamId);
        return teamInvitationMapper.selectTeamInvitationResponses(
                teamId, TeamInvitationUtils.normalizeStatus(status));
    }

    // 查询当前用户收到的团队邀请。
    @Override
    public List<InvitationResponseVO> listMyInvitations(String status) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        expirePendingInvitationsByInviteeAccount(currentAccountId);
        return teamInvitationMapper.selectMyInvitationResponses(
                currentAccountId, TeamInvitationUtils.normalizeStatus(status));
    }

    // 处理团队邀请动作。
    @Override
    @Transactional
    public InvitationActionResponseVO handleInvitationAction(Long teamId, InvitationActionRequestVO request) {
        InvitationAction action = InvitationAction.fromValue(request.getAction());
        if (action == null) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不支持的邀请动作");
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        switch (action) {
            case INVITE:
                return invite(teamId, request, currentUserId, currentAccountId);
            case ACCEPT:
                return accept(teamId, request, currentAccountId);
            case REJECT:
                return reject(teamId, request, currentAccountId);
            case REVOKE:
                return revoke(teamId, request, currentAccountId);
            default:
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不支持的邀请动作");
        }
    }

    // 将团队待处理邀请标记为团队解散。
    @Override
    @Transactional
    public void markPendingAsTeamDissolved(Long teamId) {
        teamInvitationMapper.selectPendingInvitationsByTeamId(teamId, TeamInvitationUtils.pendingStatus())
                .forEach(invitation -> cancelPendingInvitation(
                        invitation,
                        InvitationStatus.TEAM_DISSOLVED,
                        InvitationStatus.TEAM_DISSOLVED.getDefaultReason()));
    }

    // 处理 Flowable 边界定时器触发的过期回调。
    @Override
    @Transactional
    public void expireInvitation(Long invitationId, String processInstanceId) {
        TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
        if (invitation == null || !TeamInvitationUtils.pendingStatus().equals(invitation.getStatus())) {
            return;
        }
        if (processInstanceId != null && !processInstanceId.equals(invitation.getFlowableInstanceId())) {
            return;
        }
        if (updateInvitationStatus(
                invitation.getId(), InvitationStatus.EXPIRED, InvitationStatus.EXPIRED.getDefaultReason())) {
            log.info("团队邀请已过期 invitationId={} processInstanceId={}", invitationId, processInstanceId);
        }
    }

    private InvitationActionResponseVO invite(Long teamId, InvitationActionRequestVO request,
                                              Long inviterId, Long inviterAccountId) {
        ensureActiveTeam(teamId);
        MemberRole role = TeamInvitationUtils.resolveTargetRole(request.getRoleCode());
        UserBriefBO invitee = requireInviteeAccount(request);
        if (inviterAccountId.equals(invitee.getAccountId())) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "不能邀请自己加入团队");
        }
        if (isActiveMember(teamId, invitee.getAccountId())) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "用户已是团队成员");
        }
        if (hasActivePendingInvitation(teamId, invitee.getAccountId())) {
            throw new BusinessException(ResultCode.USER_DUPLICATE_REQUEST, "用户已有待处理邀请");
        }

        Long expireSeconds = request.getExpireSeconds() != null && request.getExpireSeconds() > 0
                ? request.getExpireSeconds() : INVITATION_EXPIRE_SECONDS;
        PendingInvitationBO pendingInvitation = PendingInvitationBO.of(
                teamId, inviterId, inviterAccountId, invitee.getUserId(), invitee.getAccountId(), role.getValue(),
                LocalDateTime.now().plusSeconds(expireSeconds));
        TeamInvitation invitation = teamInvitationConverter.toPendingInvitation(pendingInvitation);
        teamInvitationMapper.insert(invitation);
        String processInstanceId = workflowService.startInvitation(
                invitation.getId(), teamId, inviterAccountId, invitee.getAccountId(), role.getValue(), expireSeconds);
        teamInvitationMapper.updateFlowableInstanceId(invitation.getId(), processInstanceId);

        log.info("团队邀请已发起 teamId={} invitationId={} inviterId={} inviteeId={} role={}",
                teamId, invitation.getId(), inviterId, invitee.getUserId(), role.getValue());
        return toActionResponse(InvitationAction.INVITE, invitation.getId(), null);
    }

    private InvitationActionResponseVO accept(Long teamId, InvitationActionRequestVO request, Long currentAccountId) {
        TeamInvitation invitation = requirePendingInvitation(teamId, request.getInvitationId());
        ensureInvitee(invitation, currentAccountId);
        if (isActiveMember(teamId, currentAccountId)) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "用户已是团队成员");
        }

        TeamMember member = teamInvitationConverter.toActiveMember(invitation, LocalDateTime.now());
        requireAffected(teamMemberMapper.upsertAcceptedMember(member), "团队成员状态已变化，请刷新后重试");
        TeamMember activeMember = teamMemberMapper.selectActiveMemberByAccount(teamId, currentAccountId);
        if (activeMember == null) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "团队成员状态已变化，请刷新后重试");
        }
        requireStatusUpdated(invitation.getId(), InvitationStatus.ACCEPTED, null);
        workflowService.completeInvitation(invitation.getFlowableInstanceId(), InvitationAction.ACCEPT.getValue());
        teamPermissionService.reloadPermissionCache();

        log.info("团队邀请已接受 teamId={} invitationId={} accountId={}", teamId, invitation.getId(), currentAccountId);
        TeamMemberResponseVO memberVO = teamInvitationConverter.toMemberResponseVO(
                activeMember, requireAccount(currentAccountId));
        return toActionResponse(InvitationAction.ACCEPT, invitation.getId(), memberVO);
    }

    private InvitationActionResponseVO reject(Long teamId, InvitationActionRequestVO request, Long currentAccountId) {
        TeamInvitation invitation = requirePendingInvitation(teamId, request.getInvitationId());
        ensureInvitee(invitation, currentAccountId);
        requireStatusUpdated(invitation.getId(), InvitationStatus.REJECTED, request.getReason());
        workflowService.completeInvitation(invitation.getFlowableInstanceId(), InvitationAction.REJECT.getValue());

        log.info("团队邀请已拒绝 teamId={} invitationId={} accountId={}", teamId, invitation.getId(), currentAccountId);
        return toActionResponse(InvitationAction.REJECT, invitation.getId(), null);
    }

    private InvitationActionResponseVO revoke(Long teamId, InvitationActionRequestVO request, Long currentAccountId) {
        TeamInvitation invitation = requirePendingInvitation(teamId, request.getInvitationId());
        if (!currentAccountId.equals(invitation.getInviterAccountId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "只能撤销自己发起的邀请");
        }
        cancelPendingInvitation(invitation, InvitationStatus.REVOKED, InvitationAction.REVOKE.getDefaultReason());

        log.info("团队邀请已撤销 teamId={} invitationId={} accountId={}", teamId, invitation.getId(), currentAccountId);
        return toActionResponse(InvitationAction.REVOKE, invitation.getId(), null);
    }

    private TeamInvitation requirePendingInvitation(Long teamId, Long invitationId) {
        if (invitationId == null) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "邀请ID不能为空");
        }
        ensureActiveTeam(teamId);
        TeamInvitation invitation = teamInvitationMapper.selectPendingInvitation(
                teamId, invitationId, TeamInvitationUtils.pendingStatus());
        if (invitation == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "邀请不存在或已处理");
        }
        if (invitation.getExpireAt() != null && !invitation.getExpireAt().isAfter(LocalDateTime.now())) {
            expirePendingInvitationInNewTransaction(invitation.getId());
            throw new BusinessException(ResultCode.AUTHORIZATION_EXPIRED, "邀请已过期");
        }
        return invitation;
    }

    private TeamSpace ensureActiveTeam(Long teamId) {
        TeamSpace team = teamSpaceMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
        if (!TeamStatus.ACTIVE.getValue().equals(team.getStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "团队已解散");
        }
        return team;
    }

    private void cancelPendingInvitation(TeamInvitation invitation, InvitationStatus status, String reason) {
        if (!updateInvitationStatus(invitation.getId(), status, reason)) {
            return;
        }
        if (invitation.getFlowableInstanceId() != null) {
            workflowService.cancelInvitation(invitation.getFlowableInstanceId(), reason);
        }
    }

    private void requireStatusUpdated(Long invitationId, InvitationStatus status, String reason) {
        if (!updateInvitationStatus(invitationId, status, reason)) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "邀请已处理，不能重复操作");
        }
    }

    private boolean updateInvitationStatus(Long invitationId, InvitationStatus status, String reason) {
        return teamInvitationMapper.updatePendingInvitationStatus(
                invitationId, TeamInvitationUtils.pendingStatus(), status.getValue(), reason) > 0;
    }

    private void expirePendingInvitationsByTeam(Long teamId) {
        teamInvitationMapper.expirePendingInvitationsByTeam(
                teamId,
                TeamInvitationUtils.pendingStatus(),
                InvitationStatus.EXPIRED.getValue(),
                InvitationStatus.EXPIRED.getDefaultReason(),
                LocalDateTime.now());
    }

    private void expirePendingInvitationsByInviteeAccount(Long inviteeAccountId) {
        teamInvitationMapper.expirePendingInvitationsByInviteeAccount(
                inviteeAccountId,
                TeamInvitationUtils.pendingStatus(),
                InvitationStatus.EXPIRED.getValue(),
                InvitationStatus.EXPIRED.getDefaultReason(),
                LocalDateTime.now());
    }

    private void requireAffected(int affectedRows, String message) {
        if (affectedRows <= 0) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, message);
        }
    }

    private void expirePendingInvitationInNewTransaction(Long invitationId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> updateInvitationStatus(
                invitationId, InvitationStatus.EXPIRED, InvitationStatus.EXPIRED.getDefaultReason()));
    }

    private InvitationActionResponseVO toActionResponse(InvitationAction action, Long invitationId,
                                                        TeamMemberResponseVO member) {
        InvitationResponseVO invitationVO = teamInvitationMapper.selectInvitationResponseById(invitationId);
        return teamInvitationConverter.toActionResponseVO(
                action.getValue(), invitationVO, member, action.getSuccessMessage());
    }

    private UserBriefBO requireInviteeAccount(InvitationActionRequestVO request) {
        if (request.getInviteeAccountId() != null) {
            return requireAccount(request.getInviteeAccountId());
        }
        if (request.getInviteeUserId() != null) {
            UserBriefBO user = sysUserMapper.selectUserBriefById(request.getInviteeUserId());
            if (user == null || user.getAccountId() == null) {
                throw new BusinessException(ResultCode.USER_NOT_EXIST, "账户不存在");
            }
            return user;
        }
        throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "账户ID不能为空");
    }

    private UserBriefBO requireAccount(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "账户ID不能为空");
        }
        UserBriefBO user = sysUserMapper.selectUserBriefByAccountId(accountId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST, "账户不存在");
        }
        return user;
    }

    private boolean isActiveMember(Long teamId, Long accountId) {
        return teamInvitationMapper.existsActiveMemberByAccount(teamId, accountId, MemberStatus.ACTIVE.getValue());
    }

    private boolean hasActivePendingInvitation(Long teamId, Long inviteeAccountId) {
        return teamInvitationMapper.existsActivePendingInvitation(
                teamId, inviteeAccountId, TeamInvitationUtils.pendingStatus(), LocalDateTime.now());
    }

    private void ensureInvitee(TeamInvitation invitation, Long accountId) {
        if (!accountId.equals(invitation.getInviteeAccountId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "只能处理发给当前账户的邀请");
        }
    }

    // 获取邀请流程图表数据。
    @Override
    public ProcessDiagramResponseVO getProcessDiagram(Long invitationId) {
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "邀请不存在");
        }
        if (!currentAccountId.equals(invitation.getInviterAccountId())
                && !currentAccountId.equals(invitation.getInviteeAccountId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "无权查看该邀请流程");
        }
        if (invitation.getFlowableInstanceId() == null) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "邀请流程尚未启动");
        }
        return workflowService.getProcessDiagram(invitation.getFlowableInstanceId());
    }

}
