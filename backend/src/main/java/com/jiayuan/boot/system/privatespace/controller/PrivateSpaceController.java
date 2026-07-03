package com.jiayuan.boot.system.privatespace.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivatePasswordRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSpaceStatusResponseVO;
import com.jiayuan.boot.system.privatespace.service.PrivateSpaceService;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 私密空间管理控制器。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Tag(name = "私密空间管理接口")
@RestController
@RequestMapping("/api/v1/personal/private-space")
@RequiredArgsConstructor
public class PrivateSpaceController {

    private final PrivateSpaceService privateSpaceService;

    @GetMapping("/status")
    @Operation(summary = "获取私密空间状态")
    public Result<PrivateSpaceStatusResponseVO> getStatus() {
        return Result.success(privateSpaceService.getStatus());
    }

    @PutMapping("/password")
    @Operation(summary = "设置或修改私密空间密码")
    public Result<?> updatePassword(@Valid @RequestBody PrivatePasswordRequestVO request) {
        privateSpaceService.updatePassword(request);
        return Result.success();
    }

    @PostMapping("/session")
    @Operation(summary = "解锁私密空间会话")
    public Result<PrivateSessionResponseVO> unlock(@Valid @RequestBody PrivateSessionRequestVO request) {
        return Result.success(privateSpaceService.unlock(request));
    }

    @GetMapping("/directories")
    @Operation(summary = "列出私密空间目录")
    public Result<List<DirectoryNodeResponseVO>> listPrivateDirectories(
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(privateSpaceService.listDirectories(parentId));
    }

    @PostMapping("/directories")
    @Operation(summary = "新建私密空间目录")
    public Result<FileInfoResponseVO> createPrivateDirectory(@Valid @RequestBody DirectoryCreateRequestVO request) {
        return Result.success(privateSpaceService.createDirectory(request));
    }

    @GetMapping("/files")
    @Operation(summary = "列出私密空间文件")
    public Result<FileListResponseVO> listPrivateFiles(
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(privateSpaceService.listFiles(parentId));
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传私密空间文件")
    public Result<FileInfoResponseVO> uploadPrivateFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(privateSpaceService.uploadFile(file, parentId));
    }

    @GetMapping("/files/{id}")
    @Operation(summary = "获取私密空间文件详情")
    public Result<FileInfoResponseVO> getPrivateFile(@PathVariable Long id) {
        return Result.success(privateSpaceService.getFile(id));
    }

    @GetMapping("/files/{id}/download")
    @Operation(summary = "下载私密空间文件")
    public void downloadPrivateFile(@PathVariable Long id, HttpServletResponse response) {
        privateSpaceService.downloadFile(id, response);
    }

    @PutMapping("/files/{id}/move")
    @Operation(summary = "移动私密空间文件")
    public Result<Void> movePrivateFile(@PathVariable Long id,
                                        @Valid @RequestBody FileMoveRequestVO request) {
        privateSpaceService.moveFile(id, request.getTargetDirectoryId());
        return Result.success();
    }

    @DeleteMapping("/files/{id}")
    @Operation(summary = "删除私密空间文件")
    public Result<Void> deletePrivateFile(@PathVariable Long id) {
        privateSpaceService.deleteToTrash(id);
        return Result.success();
    }

    @GetMapping("/trash")
    @Operation(summary = "列出私密空间回收站文件")
    public Result<List<RecycleBinItemResponseVO>> listPrivateTrash() {
        return Result.success(privateSpaceService.listTrash());
    }

    @PostMapping("/trash/{id}/restore")
    @Operation(summary = "恢复私密空间回收站文件")
    public Result<FileInfoResponseVO> restorePrivateTrash(@PathVariable Long id,
                                                          @RequestParam(required = false) ConflictPolicy conflictPolicy) {
        return Result.success(privateSpaceService.restoreTrash(id, conflictPolicy));
    }

    @DeleteMapping("/trash/{id}")
    @Operation(summary = "永久删除私密空间回收站文件")
    public Result<Void> permanentlyDeletePrivateTrash(@PathVariable Long id) {
        privateSpaceService.permanentlyDeleteTrash(id);
        return Result.success();
    }
}
