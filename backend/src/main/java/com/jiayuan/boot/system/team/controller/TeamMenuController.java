package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.team.model.vo.MenuTreeResponseVO;
import com.jiayuan.boot.system.team.service.TeamMenuService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队菜单控制器。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@RestController
@RequiredArgsConstructor
public class TeamMenuController {

    private final TeamMenuService teamMenuService;

    @GetMapping("/api/v1/me/menus")
    @Operation(summary = "获取我的菜单")
    public Result<MenuTreeResponseVO> listMyMenus() {
        return Result.success(teamMenuService.listMyMenus());
    }

    @GetMapping("/api/v1/team/{teamId}/menus")
    @Operation(summary = "获取团队菜单")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<MenuTreeResponseVO> listTeamMenus(@PathVariable("teamId") Long id) {
        return Result.success(teamMenuService.listTeamMenus(id));
    }
}
