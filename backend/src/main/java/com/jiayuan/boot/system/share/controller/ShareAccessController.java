package com.jiayuan.boot.system.share.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareDownloadResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareVerifyRequestVO;
import com.jiayuan.boot.system.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 分享链接公开访问控制器（匿名可访问，路径 /api/v1/s/** 已在 security.permit-paths 放行）
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Tag(name = "分享链接访问接口")
@RestController
@RequestMapping("/api/v1/s")
@RequiredArgsConstructor
public class ShareAccessController {

    private final ShareService shareService;

    @GetMapping("/{shareToken}")
    @Operation(summary = "访问分享链接，获取被分享文件的基本信息")
    public Result<ShareAccessResponseVO> getShare(
            @PathVariable String shareToken) {
        return Result.success(shareService.getShareByToken(shareToken));
    }

    @PostMapping("/{shareToken}/verify")
    @Operation(summary = "校验分享提取码")
    public Result<?> verifyExtractCode(
            @PathVariable String shareToken,
            @Valid @RequestBody ShareVerifyRequestVO request) {
        shareService.verifyExtractCode(shareToken, request.getExtractCode());
        return Result.success();
    }

    @GetMapping("/{shareToken}/download")
    @Operation(summary = "获取分享文件的后端受控下载 URL")
    public Result<ShareDownloadResponseVO> getDownloadUrl(
            @PathVariable String shareToken,
            @RequestParam(value = "code", required = false) String extractCode,
            @RequestParam(value = "fileId", required = false) Long id) {
        return Result.success(shareService.getDownloadUrl(shareToken, extractCode, id));
    }

    @GetMapping("/{shareToken}/download/file")
    @Operation(summary = "下载分享文件")
    public void downloadSharedFile(
            @PathVariable String shareToken,
            @RequestParam(value = "code", required = false) String extractCode,
            @RequestParam(value = "fileId", required = false) Long id,
            HttpServletResponse response) {
        shareService.downloadFile(shareToken, extractCode, id, response);
    }

    @GetMapping(value = "/{shareToken}/download/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 进度下载分享文件")
    public SseEmitter downloadWithProgress(
            @PathVariable String shareToken,
            @RequestParam(value = "code", required = false) String extractCode,
            @RequestParam(value = "fileId", required = false) Long id) {
        return shareService.downloadWithProgress(shareToken, extractCode, id);
    }

    @GetMapping("/{shareToken}/children")
    @Operation(summary = "列出分享目录内容")
    public Result<FileListResponseVO> getSShareTokenChildren(
            @PathVariable String shareToken,
            @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId,
            @RequestParam(value = "code", required = false) String extractCode) {
        return Result.success(shareService.listSharedChildren(shareToken, parentId, extractCode));
    }

}
