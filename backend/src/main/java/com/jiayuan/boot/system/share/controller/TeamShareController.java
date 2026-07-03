package com.jiayuan.boot.system.share.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import com.jiayuan.boot.system.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队分享管理控制器。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Tag(name = "团队分享接口")
@RestController
@RequestMapping("/api/v1/team/{teamId}/shares")
@RequiredArgsConstructor
public class TeamShareController {

    private final ShareService shareService;

    @PostMapping
    @Operation(summary = "创建团队分享")
    @PreAuthorize("@requireTeamPerm.hasPerm('share:create')")
    public Result<ShareInfoResponseVO> createTeamShare(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody ShareCreateRequestVO request) {
        return Result.success(shareService.createTeamShare(id, request));
    }

    @GetMapping
    @Operation(summary = "列出团队全部分享")
    @PreAuthorize("@requireTeamPerm.hasPerm('share:manage')")
    public Result<List<ShareInfoResponseVO>> listTeamShares(@PathVariable("teamId") Long id) {
        return Result.success(shareService.listTeamShares(id));
    }

    @GetMapping("/{shareId}")
    @Operation(summary = "获取团队分享详情")
    @PreAuthorize("@requireTeamPerm.hasPerm('share:manage')")
    public Result<ShareInfoResponseVO> getTeamShare(
            @PathVariable("teamId") Long id,
            @PathVariable("shareId") Long parentId) {
        return Result.success(shareService.getTeamShare(id, parentId));
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "取消团队分享")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<Void> cancelTeamShare(
            @PathVariable("teamId") Long id,
            @PathVariable("shareId") Long parentId) {
        shareService.cancelTeamShare(id, parentId);
        return Result.success();
    }
}
