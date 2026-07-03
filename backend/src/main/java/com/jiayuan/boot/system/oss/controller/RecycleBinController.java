package com.jiayuan.boot.system.oss.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.oss.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回收站接口（Bonus 4.3）
 * <p>
 * 常规删除 {@code DELETE /api/v1/files/{id}} 会把文件放入回收站（{@code in_recycle_bin=1}），
 * 用户可通过本控制器列出、恢复、永久删除。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Tag(name = "回收站接口")
@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final FileService fileService;

    @GetMapping
    @Operation(summary = "列出个人回收站文件/目录")
    public Result<List<RecycleBinItemResponseVO>> list() {
        return Result.success(fileService.listRecycleBin());
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复个人回收站文件/目录")
    public Result<?> restore(@PathVariable Long id) {
        fileService.restoreFromRecycleBin(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "永久删除回收站文件")
    public Result<?> permanentDelete(@PathVariable Long id) {
        fileService.permanentlyDelete(id);
        return Result.success();
    }

}
