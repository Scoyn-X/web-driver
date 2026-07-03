package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamTrashItemResponseVO;
import com.jiayuan.boot.system.team.service.TeamFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队回收站控制器。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Tag(name = "团队回收站接口")
@RestController
@RequestMapping("/api/v1/team/{teamId}/trash")
@RequiredArgsConstructor
public class TeamTrashController {

    private final TeamFileService teamFileService;

    @GetMapping
    @Operation(summary = "列出团队回收站文件/目录")
    @PreAuthorize("@requireTeamPerm.hasPerm('trash:list')")
    public Result<List<TeamTrashItemResponseVO>> listTrash(@PathVariable("teamId") Long id) {
        return Result.success(teamFileService.listTrash(id));
    }

    @PostMapping("/{trashId}/restore")
    @Operation(summary = "恢复团队回收站文件/目录")
    @PreAuthorize("@requireTeamPerm.hasPerm('trash:restore')")
    public Result<TeamFileResponseVO> restoreTrash(@PathVariable("teamId") Long id,
                                                   @PathVariable("trashId") Long parentId,
                                                   @RequestParam(value = "conflictPolicy", required = false)
                                                   ConflictPolicy conflictPolicy) {
        try {
            return Result.success(teamFileService.restoreTrash(id, parentId, conflictPolicy));
        } catch (TeamFileService.RestoreConflictException e) {
            Result<TeamFileResponseVO> result = Result.failed(e.getResultCode(), e.getMessage());
            result.setData(e.getConflictFile());
            return result;
        }
    }

    @DeleteMapping("/{trashId}")
    @Operation(summary = "永久删除回收站文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('trash:delete')")
    public Result<Void> permanentlyDeleteTrash(@PathVariable("teamId") Long id,
                                               @PathVariable("trashId") Long parentId) {
        teamFileService.permanentlyDeleteTrash(id, parentId);
        return Result.success();
    }
}
