package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.team.model.vo.PermissionResponseVO;
import com.jiayuan.boot.system.team.model.vo.RoleOptionResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队权限查询控制器。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Tag(name = "团队权限接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamPermissionController {

    private final TeamPermissionService teamPermissionService;

    @GetMapping("/permissions")
    @Operation(summary = "列出系统权限点")
    public Result<List<PermissionResponseVO>> listPermissions() {
        return Result.success(teamPermissionService.listPermissions());
    }

    @GetMapping("/roles")
    @Operation(summary = "列出系统角色")
    public Result<List<RoleOptionResponseVO>> listRoleOptions() {
        return Result.success(teamPermissionService.listRoles());
    }

    @GetMapping("/team/{teamId}/permissions")
    @Operation(summary = "获取团队权限")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<TeamPermissionResponseVO> getTeamPermissions(
            @PathVariable("teamId") Long id) {
        return Result.success(teamPermissionService.getTeamPermissions(id));
    }
}
