package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.converter.TeamInvitationConverter;
import com.jiayuan.boot.system.team.model.enums.InvitationAction;
import com.jiayuan.boot.system.team.model.vo.CreateInvitationRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionResponseVO;
import com.jiayuan.boot.system.team.model.vo.InvitationResponseVO;
import com.jiayuan.boot.system.team.service.TeamInvitationService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队邀请控制器。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@RestController
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;
    private final TeamPermissionService teamPermissionService;
    private final TeamInvitationConverter teamInvitationConverter;

    @GetMapping("/api/v1/team/{teamId}/invitations")
    @Operation(summary = "列出团队邀请")
    @PreAuthorize("@requireTeamPerm.hasPerm('member:invite')")
    public Result<List<InvitationResponseVO>> listTeamInvitations(
            @PathVariable("teamId") Long id,
            @RequestParam(required = false) String status) {
        return Result.success(teamInvitationService.listTeamInvitations(id, status));
    }

    @PostMapping("/api/v1/team/{teamId}/invitations")
    @Operation(summary = "发起邀请")
    @PreAuthorize("@requireTeamPerm.hasPerm('member:invite')")
    public Result<InvitationActionResponseVO> createInvitation(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody CreateInvitationRequestVO request) {
        return Result.success(teamInvitationService.handleInvitationAction(
                id, teamInvitationConverter.toInviteActionRequest(request)));
    }

    @PostMapping("/api/v1/team/{teamId}/invitations/actions")
    @Operation(summary = "统一处理团队邀请动作")
    public Result<InvitationActionResponseVO> handleInvitationAction(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody InvitationActionRequestVO request) {
        InvitationAction action = InvitationAction.fromValue(request.getAction());
        if (action != null && action.requiresInvitePermission()) {
            teamPermissionService.checkPermission(
                    id, SecurityUtils.getCurrentAccountId(), TeamPermissionService.MEMBER_INVITE_PERMISSION);
        }
        return Result.success(teamInvitationService.handleInvitationAction(id, request));
    }

    @GetMapping("/api/v1/users/me/team-invitations")
    @Operation(summary = "列出我收到的团队邀请")
    public Result<List<InvitationResponseVO>> listMyInvitations(
            @RequestParam(required = false) String status) {
        return Result.success(teamInvitationService.listMyInvitations(status));
    }

    @GetMapping("/api/v1/invitations/{id}/process-diagram")
    @Operation(summary = "获取邀请流程图表")
    public Result<ProcessDiagramResponseVO> getProcessDiagram(
            @PathVariable Long id) {
        return Result.success(teamInvitationService.getProcessDiagram(id));
    }

    @GetMapping("/api/v1/team/invitations/received")
    @Operation(summary = "列出我收到的团队邀请")
    public Result<List<InvitationResponseVO>> listReceivedInvitations(
            @RequestParam(required = false) String status) {
        return Result.success(teamInvitationService.listMyInvitations(status));
    }

    @PutMapping("/api/v1/team/{teamId}/invitations/{invitationId}/accept")
    @Operation(summary = "接受邀请")
    public Result<InvitationActionResponseVO> acceptInvitation(
            @PathVariable("teamId") Long id,
            @PathVariable("invitationId") Long parentId) {
        InvitationActionRequestVO request = new InvitationActionRequestVO();
        request.setAction("ACCEPT");
        request.setInvitationId(parentId);
        return Result.success(teamInvitationService.handleInvitationAction(id, request));
    }

    @PutMapping("/api/v1/team/{teamId}/invitations/{invitationId}/reject")
    @Operation(summary = "拒绝邀请")
    public Result<InvitationActionResponseVO> rejectInvitation(
            @PathVariable("teamId") Long id,
            @PathVariable("invitationId") Long parentId) {
        InvitationActionRequestVO request = new InvitationActionRequestVO();
        request.setAction("REJECT");
        request.setInvitationId(parentId);
        return Result.success(teamInvitationService.handleInvitationAction(id, request));
    }
}
