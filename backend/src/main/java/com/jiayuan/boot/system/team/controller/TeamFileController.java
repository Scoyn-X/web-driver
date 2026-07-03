package com.jiayuan.boot.system.team.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileCopyRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferFromPersonalRequestVO;
import com.jiayuan.boot.system.team.model.vo.TransferToPersonalRequestVO;
import com.jiayuan.boot.system.team.service.TeamFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 团队文件控制器。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Tag(name = "团队文件接口")
@RestController
@RequestMapping("/api/v1/team/{teamId}")
@RequiredArgsConstructor
public class TeamFileController {

    private final TeamFileService teamFileService;

    @GetMapping("/directories")
    @Operation(summary = "列出团队目录")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<List<DirectoryNodeResponseVO>> listDirectories(
            @PathVariable("teamId") Long id,
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(teamFileService.listDirectories(id, parentId));
    }

    @PostMapping("/directories")
    @Operation(summary = "创建团队目录")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:upload')")
    public Result<TeamFileResponseVO> createTeamDirectory(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody DirectoryCreateRequestVO request) {
        return Result.success(teamFileService.createDirectory(id, request));
    }

    @PutMapping("/directories/{directoryId}/rename")
    @Operation(summary = "重命名团队目录")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:move')")
    public Result<TeamFileResponseVO> renameTeamDirectory(
            @PathVariable("teamId") Long id,
            @PathVariable("directoryId") Long parentId,
            @Valid @RequestBody DirectoryRenameRequestVO request) {
        return Result.success(teamFileService.renameDirectory(id, parentId, request));
    }

    @GetMapping("/files")
    @Operation(summary = "列出团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<FileListResponseVO> listTeamFiles(
            @PathVariable("teamId") Long id,
            @RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(teamFileService.listFiles(id, parentId));
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:upload')")
    public Result<TeamFileResponseVO> uploadTeamFile(
            @PathVariable("teamId") Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId) {
        return Result.success(teamFileService.uploadFile(id, file, parentId));
    }

    @GetMapping("/files/{fileId}")
    @Operation(summary = "获取团队文件详情")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:detail')")
    public Result<TeamFileResponseVO> getTeamFile(@PathVariable("teamId") Long id,
                                                  @PathVariable("fileId") Long parentId) {
        return Result.success(teamFileService.getFile(id, parentId));
    }

    @GetMapping("/files/{fileId}/download")
    @Operation(summary = "下载团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:download')")
    public void downloadTeamFile(@PathVariable("teamId") Long id,
                                 @PathVariable("fileId") Long parentId,
                                 HttpServletResponse response) {
        teamFileService.downloadFile(id, parentId, response);
    }

    @PutMapping("/files/{fileId}/move")
    @Operation(summary = "移动团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:move')")
    public Result<Void> moveTeamFile(@PathVariable("teamId") Long id,
                                     @PathVariable("fileId") Long parentId,
                                     @Valid @RequestBody FileMoveRequestVO request) {
        teamFileService.moveFile(id, parentId, request.getTargetDirectoryId());
        return Result.success();
    }

    @PostMapping("/files/{fileId}/copy")
    @Operation(summary = "复制团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:copy')")
    public Result<TeamFileResponseVO> copyTeamFile(@PathVariable("teamId") Long id,
                                                   @PathVariable("fileId") Long parentId,
                                                   @Valid @RequestBody FileCopyRequestVO request) {
        return Result.success(teamFileService.copyFile(id, parentId, request.getTargetDirectoryId()));
    }

    @PostMapping("/files/from-personal")
    @Operation(summary = "转存个人文件到团队")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:transfer:to-team')")
    public Result<TeamFileResponseVO> transferFromPersonal(
            @PathVariable("teamId") Long id,
            @Valid @RequestBody TransferFromPersonalRequestVO request) {
        return Result.success(teamFileService.transferFromPersonal(id, request));
    }

    @PostMapping("/files/{fileId}/save-to-personal")
    @Operation(summary = "转存团队文件到个人空间")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:transfer:to-personal')")
    public Result<FileInfoResponseVO> transferToPersonal(
            @PathVariable("teamId") Long id,
            @PathVariable("fileId") Long parentId,
            @Valid @RequestBody TransferToPersonalRequestVO request) {
        return Result.success(teamFileService.transferToPersonal(id, parentId, request));
    }

    @DeleteMapping("/files/{fileId}")
    @Operation(summary = "删除团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:delete')")
    public Result<Void> deleteToTrash(@PathVariable("teamId") Long id,
                                      @PathVariable("fileId") Long parentId) {
        teamFileService.deleteToTrash(id, parentId);
        return Result.success();
    }

    @GetMapping("/files/search")
    @Operation(summary = "搜索团队文件")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<List<TeamFileResponseVO>> searchTeamFiles(@PathVariable("teamId") Long id,
                                                            @RequestParam String keyword) {
        return Result.success(teamFileService.searchFiles(id, keyword));
    }

    @GetMapping("/files/tree")
    @Operation(summary = "列出团队文件树")
    @PreAuthorize("@requireTeamPerm.hasPerm('file:list')")
    public Result<List<FileTreeResponseVO>> listTeamFileTree(@PathVariable("teamId") Long id) {
        return Result.success(teamFileService.listFileTree(id));
    }

}
