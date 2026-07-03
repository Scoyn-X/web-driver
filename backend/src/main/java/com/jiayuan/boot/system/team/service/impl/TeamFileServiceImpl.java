package com.jiayuan.boot.system.team.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.team.converter.TeamTrashConverter;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamTrashItemResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferFromPersonalRequestVO;
import com.jiayuan.boot.system.team.model.vo.TransferToPersonalRequestVO;
import com.jiayuan.boot.system.team.service.TeamFileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队文件服务实现。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Service
@RequiredArgsConstructor
public class TeamFileServiceImpl implements TeamFileService {

    private static final String SPACE_TYPE_TEAM = "TEAM";
    private static final long ROOT_ID = 0L;

    private final SysFileMapper sysFileMapper;
    private final SysFileConverter sysFileConverter;
    private final SysUserMapper sysUserMapper;
    private final FileObjectService fileObjectService;
    private final TeamFileWriteService teamFileWriteService;
    private final TeamFileLookupService teamFileLookupService;
    private final TeamTrashConverter teamTrashConverter;

    /**
     * 列出团队目录。
     */
    @Override
    public List<DirectoryNodeResponseVO> listDirectories(Long teamId, Long parentId) {
        Long resolvedParentId = teamFileLookupService.resolveParentId(parentId);
        teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedParentId);

        List<SysFile> directories = sysFileMapper.selectTeamDirectories(teamId, resolvedParentId);
        if (directories.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> directoryIds = directories.stream().map(SysFile::getId).toList();
        Set<Long> idsHavingChildren = new HashSet<>(
                sysFileMapper.selectTeamParentIdsHavingChildDirectory(teamId, directoryIds));
        return directories.stream()
                .map(directory -> sysFileConverter.toDirectoryNodeVO(
                        directory, idsHavingChildren.contains(directory.getId())))
                .toList();
    }

    /**
     * 创建团队目录。
     */
    @Override
    @Transactional
    public TeamFileResponseVO createDirectory(Long teamId, DirectoryCreateRequestVO request) {
        return buildTeamFileResponse(teamFileWriteService.createDirectory(teamId, request));
    }

    /**
     * 重命名团队目录。
     */
    @Override
    @Transactional
    public TeamFileResponseVO renameDirectory(Long teamId, Long directoryId, DirectoryRenameRequestVO request) {
        return buildTeamFileResponse(teamFileWriteService.renameDirectory(teamId, directoryId, request));
    }

    /**
     * 上传团队文件。
     */
    @Override
    @Transactional
    public TeamFileResponseVO uploadFile(Long teamId, MultipartFile file, Long parentId) {
        return buildTeamFileResponse(teamFileWriteService.uploadFile(teamId, file, parentId));
    }

    /**
     * 列出团队文件。
     */
    @Override
    public FileListResponseVO listFiles(Long teamId, Long parentId) {
        Long resolvedParentId = teamFileLookupService.resolveParentId(parentId);
        teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedParentId);

        return sysFileConverter.toFileListResponseVO(
                sysFileConverter.toFileInfoVOList(sysFileMapper.selectTeamChildren(teamId, resolvedParentId)),
                buildTeamBreadcrumb(teamId, resolvedParentId));
    }

    /**
     * 获取团队文件详情。
     */
    @Override
    public TeamFileResponseVO getFile(Long teamId, Long fileId) {
        return buildTeamFileResponse(teamFileLookupService.requireActiveTeamFile(teamId, fileId));
    }

    /**
     * 下载团队文件。
     */
    @Override
    public void downloadFile(Long teamId, Long fileId, HttpServletResponse response) {
        SysFile file = teamFileLookupService.requireActiveTeamFile(teamId, fileId);
        if (Integer.valueOf(1).equals(file.getIsDirectory())) {
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "不支持下载目录");
        }
        fileObjectService.writeToResponse(file, response);
    }

    /**
     * 移动团队文件或目录。
     */
    @Override
    @Transactional
    public void moveFile(Long teamId, Long fileId, Long targetDirectoryId) {
        teamFileWriteService.moveFile(teamId, fileId, targetDirectoryId);
    }

    /**
     * 复制团队文件或目录。
     */
    @Override
    @Transactional
    public TeamFileResponseVO copyFile(Long teamId, Long fileId, Long targetDirectoryId) {
        return buildTeamFileResponse(teamFileWriteService.copyFile(teamId, fileId, targetDirectoryId));
    }

    /**
     * 转存个人文件或目录到团队。
     */
    @Override
    @Transactional
    public TeamFileResponseVO transferFromPersonal(Long teamId, TransferFromPersonalRequestVO request) {
        SysFile copied = teamFileWriteService.transferFromPersonal(
                teamId, request.getSourceFileId(), request.getTargetDirectoryId(), request.getConflictPolicy());
        return buildTeamFileResponse(copied);
    }

    /**
     * 转存团队文件或目录到个人空间。
     */
    @Override
    @Transactional
    public FileInfoResponseVO transferToPersonal(Long teamId, Long fileId, TransferToPersonalRequestVO request) {
        SysFile copied = teamFileWriteService.transferToPersonal(
                teamId, fileId, request.getTargetDirectoryId(), request.getConflictPolicy());
        return sysFileConverter.toFileInfoVO(copied);
    }

    /**
     * 删除团队文件或目录到回收站。
     */
    @Override
    @Transactional
    public void deleteToTrash(Long teamId, Long fileId) {
        teamFileWriteService.deleteToTrash(teamId, fileId);
    }

    /**
     * 列出团队回收站根节点。
     */
    @Override
    public List<TeamTrashItemResponseVO> listTrash(Long teamId) {
        List<SysFile> files = sysFileMapper.selectTeamTrashRoots(teamId);
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> idToName = resolveTrashAncestorNames(teamId, files);
        Map<Long, UserBriefBO> deleterBriefMap = resolveDeleterBriefs(files);
        return files.stream()
                .map(file -> buildTrashItemResponse(file, idToName, deleterBriefMap))
                .toList();
    }

    /**
     * 恢复团队回收站根节点。
     */
    @Override
    @Transactional
    public TeamFileResponseVO restoreTrash(Long teamId, Long trashId, ConflictPolicy conflictPolicy) {
        try {
            return buildTeamFileResponse(teamFileWriteService.restoreFromTrash(teamId, trashId, conflictPolicy));
        } catch (TeamFileWriteService.RestoreNameConflictException e) {
            throw new TeamFileService.RestoreConflictException(buildTeamFileResponse(e.getConflictFile()));
        }
    }

    /**
     * 永久删除团队回收站根节点。
     */
    @Override
    @Transactional
    public void permanentlyDeleteTrash(Long teamId, Long trashId) {
        teamFileWriteService.permanentlyDeleteTrash(teamId, trashId);
    }

    /**
     * 搜索团队文件。
     */
    @Override
    public List<TeamFileResponseVO> searchFiles(Long teamId, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        return buildTeamFileResponses(sysFileMapper.searchTeamFiles(teamId, keyword));
    }

    /**
     * 列出团队文件树。
     */
    @Override
    public List<FileTreeResponseVO> listFileTree(Long teamId) {
        Map<Long, List<FileTreeResponseVO>> childrenMap = sysFileMapper.selectTeamActiveTree(teamId).stream()
                .map(sysFileConverter::toFileTreeResponseVO)
                .collect(Collectors.groupingBy(FileTreeResponseVO::getParentId));
        return buildFileTree(childrenMap, ROOT_ID);
    }

    private TeamFileResponseVO buildTeamFileResponse(SysFile file) {
        String uploaderName = resolveUploaderNames(List.of(file)).get(file.getUploaderId());
        return sysFileConverter.toTeamFileResponseVO(file, uploaderName);
    }

    private List<TeamFileResponseVO> buildTeamFileResponses(List<SysFile> files) {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> uploaderNames = resolveUploaderNames(files);
        return files.stream()
                .map(file -> sysFileConverter.toTeamFileResponseVO(file, uploaderNames.get(file.getUploaderId())))
                .toList();
    }

    private Map<Long, String> resolveUploaderNames(List<SysFile> files) {
        List<Long> uploaderIds = files.stream()
                .map(SysFile::getUploaderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (uploaderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(uploaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (left, right) -> left));
    }

    private Map<Long, String> resolveTrashAncestorNames(Long teamId, List<SysFile> files) {
        Set<Long> ancestorIds = new HashSet<>();
        for (SysFile file : files) {
            List<Long> chain = StringUtils.parseIdList(file.getFullPath());
            if (chain.size() > 1) {
                ancestorIds.addAll(chain.subList(0, chain.size() - 1));
            }
        }
        if (ancestorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysFileMapper.selectFilesInSpaceByIds(SPACE_TYPE_TEAM, teamId, ancestorIds).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (left, right) -> left));
    }

    private String buildTrashPath(SysFile file, Map<Long, String> idToName) {
        List<Long> chain = StringUtils.parseIdList(file.getFullPath());
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < chain.size() - 1; i++) {
            path.append('/').append(idToName.getOrDefault(chain.get(i), "?"));
        }
        path.append('/').append(file.getOriginalName());
        return path.toString();
    }

    private TeamTrashItemResponseVO buildTrashItemResponse(SysFile file, Map<Long, String> idToName,
                                                            Map<Long, UserBriefBO> deleterBriefMap) {
        TeamTrashItemResponseVO vo = teamTrashConverter.toResponseVO(
                file,
                buildTrashPath(file, idToName),
                file.getDeletedAt(),
                TRASH_RETENTION_DAYS);
        if (file.getDeletedBy() != null) {
            vo.setDeletedByUserId(file.getDeletedBy());
            UserBriefBO deleter = deleterBriefMap.get(file.getDeletedBy());
            if (deleter != null) {
                vo.setDeletedByAccountId(deleter.getAccountId());
                vo.setDeletedByAccountName(deleter.getAccountName());
                vo.setDeletedByName(deleter.getNickname());
            } else {
                vo.setDeletedByName("未知");
            }
        }
        return vo;
    }

    private Map<Long, UserBriefBO> resolveDeleterBriefs(List<SysFile> files) {
        Set<Long> deleterIds = files.stream()
                .map(SysFile::getDeletedBy)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (deleterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectUserBriefByIds(new ArrayList<>(deleterIds)).stream()
                .collect(Collectors.toMap(UserBriefBO::getUserId, u -> u, (a, b) -> a));
    }

    private List<BreadcrumbItemResponseVO> buildTeamBreadcrumb(Long teamId, Long parentId) {
        List<BreadcrumbItemResponseVO> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItemResponseVO(ROOT_ID, "根目录"));
        if (parentId == null || ROOT_ID == parentId) {
            return crumbs;
        }
        SysFile parent = sysFileMapper.selectTeamFile(teamId, parentId);
        if (parent == null || StrUtil.isBlank(parent.getFullPath())) {
            return crumbs;
        }
        List<Long> chain = StringUtils.parseIdList(parent.getFullPath());
        Map<Long, String> idToName = sysFileMapper.selectFilesInSpaceByIds(SPACE_TYPE_TEAM, teamId, chain).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (left, right) -> left));
        for (Long id : chain) {
            crumbs.add(new BreadcrumbItemResponseVO(id, idToName.getOrDefault(id, "?")));
        }
        return crumbs;
    }

    private List<FileTreeResponseVO> buildFileTree(Map<Long, List<FileTreeResponseVO>> childrenMap, Long parentId) {
        List<FileTreeResponseVO> children = childrenMap.getOrDefault(parentId, Collections.emptyList());
        for (FileTreeResponseVO child : children) {
            if (Integer.valueOf(1).equals(child.getIsDirectory())) {
                child.setChildren(buildFileTree(childrenMap, child.getId()));
            } else {
                child.setChildren(Collections.emptyList());
            }
        }
        return children;
    }
}
