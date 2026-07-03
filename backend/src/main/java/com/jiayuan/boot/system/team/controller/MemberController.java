package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.team.model.vo.MemberRoleUpdateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferOwnerRequestVO;
import com.jiayuan.boot.system.team.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队成员管理控制器
 *
 * @author didongchen
 * @since 2026/05/20
 */
@Tag(name = "团队接口")
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{teamId}/members")
    @Operation(summary = "列出团队成员")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<List<TeamMemberResponseVO>> listMembers(
            @PathVariable("teamId") Long id) {
        return Result.success(memberService.listMembers(id));
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    @Operation(summary = "移除成员")
    @PreAuthorize("@requireTeamPerm.hasPerm('member:remove')")
    public Result<?> removeMember(
            @PathVariable("teamId") Long id,
            @PathVariable("memberId") Long member) {
        memberService.removeMember(id, member);
        return Result.success();
    }

    @DeleteMapping("/{teamId}/members/me")
    @Operation(summary = "退出团队")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<?> exitTeam(
            @PathVariable("teamId") Long id) {
        memberService.exitTeam(id);
        return Result.success();
    }

    @PutMapping("/{teamId}/members/{memberId}/role")
    @Operation(summary = "修改成员角色")
    @PreAuthorize("@requireTeamPerm.hasPerm('role:update')")
    public Result<TeamMemberResponseVO> updateMemberRole(
            @PathVariable("teamId") Long id,
            @PathVariable("memberId") Long member,
            @Valid @RequestBody MemberRoleUpdateRequestVO request) {
        return Result.success(memberService.updateMemberRole(id, member, request));
    }

    @PutMapping("/{teamId}/owner/transfer")
    @Operation(summary = "转让团队所有权")
    @PreAuthorize("@requireTeamPerm.hasPerm('owner:transfer')")
    public Result<?> transferOwner(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody TransferOwnerRequestVO request) {
        memberService.transferOwner(id, request);
        return Result.success();
    }

    @PostMapping("/{teamId}/leave")
    @Operation(summary = "退出团队")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<?> leaveTeam(
            @PathVariable("teamId") Long id) {
        memberService.exitTeam(id);
        return Result.success();
    }

    @PostMapping("/{teamId}/transfer-owner")
    @Operation(summary = "转让团队所有权")
    @PreAuthorize("@requireTeamPerm.hasPerm('owner:transfer')")
    public Result<?> transferOwnerPost(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody TransferOwnerRequestVO request) {
        memberService.transferOwner(id, request);
        return Result.success();
    }
}
