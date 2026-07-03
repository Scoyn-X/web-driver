package com.jiayuan.boot.system.share.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import com.jiayuan.boot.system.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分享控制器（需登录）
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Tag(name = "文件分享接口")
@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    @Operation(summary = "创建个人分享")
    public Result<ShareInfoResponseVO> createShare(@Valid @RequestBody ShareCreateRequestVO request) {
        return Result.success(shareService.createShare(request));
    }

    @GetMapping
    @Operation(summary = "列出个人分享")
    public Result<List<ShareInfoResponseVO>> listMyShares() {
        return Result.success(shareService.listMyShares());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "取消个人分享")
    public Result<?> cancelShare(
            @PathVariable Long id) {
        shareService.cancelShare(id);
        return Result.success();
    }

}
