package com.jiayuan.boot.system.oss.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.service.DirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目录管理控制器
 *
 * @author didongchen
 * @since 2026/04/11
 */
@Tag(name = "目录接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    @GetMapping("/directories")
    @Operation(summary = "列出个人目录")
    public Result<List<DirectoryNodeResponseVO>> listChildDirectories(
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(directoryService.listChildDirectories(parentId));
    }

    @GetMapping("/directories/tree")
    @Operation(summary = "列出个人目录树")
    public Result<List<DirectoryTreeResponseVO>> listDirectoryTree() {
        return Result.success(directoryService.listDirectoryTree());
    }

    @PostMapping("/directories")
    @Operation(summary = "新建个人目录")
    public Result<FileInfoResponseVO> createDirectory(@Valid @RequestBody DirectoryCreateRequestVO request) {
        FileInfoResponseVO directory = directoryService.createDirectory(request);
        return Result.success(directory);
    }

    @PutMapping("/directories/{id}/rename")
    @Operation(summary = "重命名个人目录")
    public Result<FileInfoResponseVO> renameDirectory(
            @PathVariable Long id,
            @Valid @RequestBody DirectoryRenameRequestVO request) {
        FileInfoResponseVO directory = directoryService.renameDirectory(id, request);
        return Result.success(directory);
    }

    @PutMapping("/personal/directories/{directoryId}/move")
    @Operation(summary = "移动个人目录")
    public Result<?> moveDirectory(
            @PathVariable("directoryId") Long id,
            @Valid @RequestBody FileMoveRequestVO request) {
        directoryService.moveDirectory(id, request);
        return Result.success();
    }

    @DeleteMapping("/directories/{id}")
    @Operation(summary = "删除个人目录")
    public Result<?> deleteDirectory(
            @PathVariable Long id) {
        boolean result = directoryService.deleteDirectory(id);
        return Result.judge(result);
    }

}
