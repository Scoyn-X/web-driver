package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.team.model.vo.TeamCreateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamDissolveRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamQuotaResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;
import com.jiayuan.boot.system.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队空间控制器
 *
 * @author didongchen
 * @since 2026/05/17
 */
@Tag(name = "团队接口")
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "创建团队")
    public Result<TeamResponseVO> createTeam(@Valid @RequestBody TeamCreateRequestVO request) {
        return Result.success(teamService.createTeam(request));
    }

    @GetMapping
    @Operation(summary = "列出我的团队")
    public Result<List<TeamResponseVO>> listUserTeams() {
        return Result.success(teamService.listUserTeams());
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "获取团队详情")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<TeamResponseVO> getTeamById(
            @PathVariable("teamId") Long id) {
        return Result.success(teamService.getTeamById(id));
    }

    @PutMapping("/{teamId}")
    @Operation(summary = "修改团队资料")
    @PreAuthorize("@requireTeamPerm.hasPerm('team:manage')")
    public Result<TeamResponseVO> updateTeam(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody TeamUpdateRequestVO request) {
        return Result.success(teamService.updateTeam(id, request));
    }

    @PostMapping("/{teamId}/dissolve")
    @Operation(summary = "解散团队")
    @PreAuthorize("@requireTeamPerm.hasPerm('team:dissolve')")
    public Result<?> dissolveTeam(
            @PathVariable("teamId") Long id,
            @RequestBody(required = false) TeamDissolveRequestVO request) {
        teamService.dissolveTeam(id);
        return Result.success();
    }

    @GetMapping("/{teamId}/quota")
    @Operation(summary = "获取团队配额")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<TeamQuotaResponseVO> getTeamQuota(
            @PathVariable("teamId") Long id) {
        return Result.success(teamService.getTeamQuota(id));
    }
}
