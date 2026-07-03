package com.jiayuan.boot.system.oss.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.entity.FileInfo;
import com.jiayuan.boot.system.oss.model.vo.FileCopyRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.service.FileService;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件控制层
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Tag(name = "文件接口")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传个人文件")
    public Result<FileInfo> uploadFile(
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId) {
        FileInfo fileInfo = fileService.uploadFile(file, parentId);
        return Result.success(fileInfo);
    }

    @GetMapping
    @Operation(summary = "列出个人文件/目录")
    public Result<FileListResponseVO> listPersonalFiles(
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(fileService.listFiles(parentId));
    }

    @GetMapping("/tree")
    @Operation(summary = "列出个人文件树")
    public Result<List<FileTreeResponseVO>> listPersonalFileTree() {
        return Result.success(fileService.listFileTree());
    }

    @GetMapping("/search")
    @Operation(summary = "搜索个人文件")
    public Result<List<FileInfoResponseVO>> searchPersonalFiles(
            @RequestParam String keyword) {
        return Result.success(fileService.searchFiles(keyword));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "按 ID 下载个人文件")
    public void downloadPersonalFileById(
            @PathVariable Long id,
            HttpServletResponse response) {
        fileService.downloadFile(id, response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除个人文件")
    public Result<?> deleteFileById(
            @PathVariable Long id) {
        boolean result = fileService.deleteFileById(id);
        return Result.judge(result);
    }

    @PostMapping("/{id}/copy")
    @Operation(summary = "复制个人文件")
    public Result<FileInfoResponseVO> copyFile(
            @PathVariable Long id,
            @Valid @RequestBody FileCopyRequestVO request) {
        FileInfoResponseVO fileInfoVO = fileService.copyFile(id, request.getTargetDirectoryId());
        return Result.success(fileInfoVO);
    }

    @PutMapping("/{id}/move")
    @Operation(summary = "移动个人文件/目录")
    public Result<FileInfoResponseVO> moveFile(
            @PathVariable Long id,
            @Valid @RequestBody FileMoveRequestVO request) {
        FileInfoResponseVO fileInfoVO = fileService.moveFile(id, request.getTargetDirectoryId());
        return Result.success(fileInfoVO);
    }

    @DeleteMapping
    @Operation(summary = "按路径删除个人文件")
    @SneakyThrows
    public Result<?> deleteFile(
            @RequestParam String filePath) {
        boolean result = fileService.deleteFile(filePath);
        return Result.judge(result);
    }

    @GetMapping("/{*filePath}")
    @Operation(summary = "按路径下载个人文件")
    public void downloadPersonalFileByPath(
            @PathVariable String filePath,
            HttpServletResponse response) {
        // Spring 的 {*filePath} 会以 / 开头，需要去掉前导斜杠
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        fileService.downloadFile(filePath, response);
    }

}
