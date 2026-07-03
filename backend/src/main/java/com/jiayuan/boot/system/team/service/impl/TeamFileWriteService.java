package com.jiayuan.boot.system.team.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.bo.FileCloneBuildBO;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.bo.TeamDirectoryBuildBO;
import com.jiayuan.boot.system.oss.model.bo.TeamFileBuildBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 团队文件写操作服务。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Component
@RequiredArgsConstructor
class TeamFileWriteService {

    private static final String SPACE_TYPE_TEAM = "TEAM";
    private static final String SPACE_TYPE_PERSONAL = "PERSONAL";
    private static final long ROOT_ID = 0L;

    private final SysFileMapper sysFileMapper;
    private final SysFileConverter sysFileConverter;
    private final FileObjectService fileObjectService;
    private final TeamQuotaService teamQuotaService;
    private final QuotaService quotaService;
    private final TeamSpaceMapper teamSpaceMapper;
    private final SystemConfigProperties configProperties;
    private final TeamFileLookupService teamFileLookupService;

    SysFile createDirectory(Long teamId, DirectoryCreateRequestVO request) {
        lockTeamSpaceForWrite(teamId);
        Long uploaderId = SecurityUtils.getCurrentUserId();
        Long parentId = teamFileLookupService.resolveParentId(request.getParentId());
        String parentFullPath = teamFileLookupService.validateTeamTargetDirectory(teamId, parentId);
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, parentId);
        validateTeamNameUnique(teamId, parentId, request.getName());

        SysFile directory = sysFileConverter.toTeamDirectory(
                new TeamDirectoryBuildBO(teamId, uploaderId, parentId, request.getName()));
        insertWithFullPath(directory, parentFullPath);
        return directory;
    }

    SysFile renameDirectory(Long teamId, Long directoryId, DirectoryRenameRequestVO request) {
        lockTeamSpaceForWrite(teamId);
        SysFile directory = teamFileLookupService.requireActiveTeamFile(teamId, directoryId);
        if (!Integer.valueOf(1).equals(directory.getIsDirectory())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队目录不存在");
        }
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, directory.getParentId());
        if (!request.getName().equals(directory.getOriginalName())) {
            validateTeamNameUnique(teamId, directory.getParentId(), request.getName());
        }

        directory.setOriginalName(request.getName());
        sysFileMapper.updateById(directory);
        return directory;
    }

    SysFile uploadFile(Long teamId, MultipartFile file, Long parentId) {
        lockTeamSpaceForWrite(teamId);
        Long uploaderId = SecurityUtils.getCurrentUserId();
        Long resolvedParentId = teamFileLookupService.resolveParentId(parentId);
        String parentFullPath = teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedParentId);
        long fileSize = file.getSize();
        teamQuotaService.checkQuota(teamId, fileSize);
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, resolvedParentId);

        String originalName = resolveUploadName(file);
        String uniqueName = generateUniqueTeamName(teamId, resolvedParentId, originalName);
        StoredFileObjectBO storedObject = null;
        boolean quotaReserved = fileSize > 0;
        if (quotaReserved) {
            teamQuotaService.increaseUsedSpace(teamId, fileSize);
        }
        try {
            storedObject = fileObjectService.saveOrReuse(file);
            SysFile sysFile = sysFileConverter.toTeamUploadedFile(
                    new TeamFileBuildBO(storedObject, teamId, uploaderId, resolvedParentId, uniqueName));
            insertWithFullPath(sysFile, parentFullPath);
            return sysFile;
        } catch (RuntimeException e) {
            rollbackUploadReservation(teamId, fileSize, quotaReserved, storedObject);
            throw e;
        }
    }

    void moveFile(Long teamId, Long fileId, Long targetDirectoryId) {
        lockTeamSpaceForWrite(teamId);
        Long resolvedTargetId = teamFileLookupService.resolveParentId(targetDirectoryId);
        SysFile source = teamFileLookupService.requireActiveTeamFile(teamId, fileId);
        if (source.getParentId().equals(resolvedTargetId)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件已在该目录下");
        }

        String targetFullPath = teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedTargetId);
        if (Integer.valueOf(1).equals(source.getIsDirectory()) && isDescendantPath(source.getId(), targetFullPath)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将目录移动到自身或子目录下");
        }
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, resolvedTargetId);
        validateTeamNameUnique(teamId, resolvedTargetId, source.getOriginalName());

        String sourceFullPath = buildChildFullPath(targetFullPath, source.getId());
        sysFileMapper.updateRootLocationInSpace(SPACE_TYPE_TEAM, teamId, source.getId(), resolvedTargetId, sourceFullPath);
        if (Integer.valueOf(1).equals(source.getIsDirectory())) {
            sysFileMapper.updateDescendantsFullPathInSpace(SPACE_TYPE_TEAM, teamId, source.getId(), sourceFullPath);
        }
    }

    SysFile copyFile(Long teamId, Long fileId, Long targetDirectoryId) {
        lockTeamSpaceForWrite(teamId);
        Long uploaderId = SecurityUtils.getCurrentUserId();
        Long resolvedTargetId = teamFileLookupService.resolveParentId(targetDirectoryId);
        SysFile source = teamFileLookupService.requireActiveTeamFile(teamId, fileId);
        String targetFullPath = teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedTargetId);
        if (Integer.valueOf(1).equals(source.getIsDirectory()) && isDescendantPath(source.getId(), targetFullPath)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将目录复制到自身或子目录下");
        }

        List<SysFile> descendants = sortedActiveDescendants(SPACE_TYPE_TEAM, teamId, source.getId());
        long totalSize = calculateLogicalSize(source, descendants);
        teamQuotaService.checkQuota(teamId, totalSize);
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, resolvedTargetId);
        SysFile copied = copyTreeToTeam(source, descendants, teamId, uploaderId,
                resolvedTargetId, targetFullPath, generateUniqueTeamName(teamId, resolvedTargetId, source.getOriginalName()));
        if (totalSize > 0) {
            teamQuotaService.increaseUsedSpace(teamId, totalSize);
        }
        return copied;
    }

    SysFile transferFromPersonal(Long teamId, Long sourceFileId, Long targetDirectoryId,
                                 ConflictPolicy ignoredConflictPolicy) {
        lockTeamSpaceForWrite(teamId);
        Long uploaderId = SecurityUtils.getCurrentUserId();
        Long resolvedTargetId = teamFileLookupService.resolveParentId(targetDirectoryId);
        SysFile source = requireActivePersonalFile(uploaderId, sourceFileId);
        String targetFullPath = teamFileLookupService.validateTeamTargetDirectory(teamId, resolvedTargetId);

        List<SysFile> descendants = sortedPersonalTransferDescendants(uploaderId, source);
        long totalSize = calculateLogicalSize(source, descendants);
        teamQuotaService.checkQuota(teamId, totalSize);
        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, resolvedTargetId);
        SysFile copied = copyTreeToTeam(source, descendants, teamId, uploaderId,
                resolvedTargetId, targetFullPath, generateUniqueTeamName(teamId, resolvedTargetId, source.getOriginalName()));
        if (totalSize > 0) {
            teamQuotaService.increaseUsedSpace(teamId, totalSize);
        }
        return copied;
    }

    SysFile transferToPersonal(Long teamId, Long sourceFileId, Long targetDirectoryId,
                               ConflictPolicy ignoredConflictPolicy) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long resolvedTargetId = teamFileLookupService.resolveParentId(targetDirectoryId);
        SysFile source = teamFileLookupService.requireActiveTeamFile(teamId, sourceFileId);
        String targetFullPath = validatePersonalTargetDirectory(userId, resolvedTargetId);

        List<SysFile> descendants = sortedTeamTransferDescendants(teamId, source);
        long totalSize = checkPersonalTransferQuota(userId, source, descendants);
        lockTargetDirectory(SPACE_TYPE_PERSONAL, userId, resolvedTargetId);
        SysFile copied = copyTreeToPersonal(source, descendants, userId, resolvedTargetId, targetFullPath,
                generateUniquePersonalName(userId, resolvedTargetId, source.getOriginalName()));
        if (totalSize > 0) {
            quotaService.increaseUsedSpace(userId, totalSize);
        }
        return copied;
    }

    void deleteToTrash(Long teamId, Long fileId) {
        lockTeamSpaceForWrite(teamId);
        SysFile root = teamFileLookupService.requireActiveTeamFile(teamId, fileId);
        moveActiveRootToTrash(teamId, root);
    }

    SysFile restoreFromTrash(Long teamId, Long trashId, ConflictPolicy conflictPolicy) {
        lockTeamSpaceForWrite(teamId);
        SysFile root = requireTeamTrashRoot(teamId, trashId);
        RestoreTarget target = resolveRestoreTarget(teamId, root);
        List<SysFile> descendants = Integer.valueOf(1).equals(root.getIsDirectory())
                ? sysFileMapper.selectDescendantsInSpace(SPACE_TYPE_TEAM, teamId, root.getId())
                : List.of();
        long totalSize = calculateLogicalSize(root, descendants);

        lockTargetDirectory(SPACE_TYPE_TEAM, teamId, target.parentId());
        String restoreName = resolveRestoreName(teamId, root, target.parentId(), conflictPolicy);
        if (totalSize > 0) {
            teamQuotaService.checkQuota(teamId, totalSize);
        }

        int restored = sysFileMapper.restoreTrashTreeInSpace(
                SPACE_TYPE_TEAM, teamId, root.getId(), target.parentId(), target.parentFullPath(), restoreName);
        if (restored == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队回收站文件状态已变化");
        }
        if (totalSize > 0) {
            teamQuotaService.increaseUsedSpace(teamId, totalSize);
        }
        return teamFileLookupService.requireActiveTeamFile(teamId, root.getId());
    }

    void permanentlyDeleteTrash(Long teamId, Long trashId) {
        lockTeamSpaceForWrite(teamId);
        SysFile root = requireTeamTrashRootForPermanentDelete(teamId, trashId);
        List<SysFile> descendants = Integer.valueOf(1).equals(root.getIsDirectory())
                ? sysFileMapper.selectDescendantsInSpace(SPACE_TYPE_TEAM, teamId, root.getId())
                : List.of();
        int deleted = sysFileMapper.permanentlyDeleteTrashTreeInSpace(SPACE_TYPE_TEAM, teamId, root.getId());
        if (deleted == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队回收站文件状态已变化");
        }
        decreaseReferencesForDeletedTree(root, descendants);
    }

    /**
     * 解散团队时永久清理团队空间内所有文件记录，并释放物理对象引用。
     */
    void permanentlyDeleteTeamSpaceFiles(Long teamId) {
        lockTeamSpaceForWrite(teamId);
        List<SysFile> files = sysFileMapper.selectFilesInSpace(SPACE_TYPE_TEAM, teamId);
        if (files.isEmpty()) {
            return;
        }
        int deleted = sysFileMapper.permanentlyDeleteSpaceFiles(SPACE_TYPE_TEAM, teamId);
        if (deleted > 0) {
            for (SysFile file : files) {
                decreaseReferenceIfFile(file);
            }
        }
    }

    private void moveActiveRootToTrash(Long teamId, SysFile root) {
        List<SysFile> descendants = sysFileMapper.selectActiveDescendantsInSpace(SPACE_TYPE_TEAM, teamId, root.getId());
        long totalSize = calculateLogicalSize(root, descendants);

        int moved = sysFileMapper.moveRootToTrashInSpace(
                SPACE_TYPE_TEAM, teamId, root.getId(), SecurityUtils.getCurrentUserId(),
                configProperties.getTrashRetentionSeconds());
        if (moved == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队文件状态已变化");
        }
        if (Integer.valueOf(1).equals(root.getIsDirectory())) {
            sysFileMapper.updateDescendantsRecycleStateInSpace(SPACE_TYPE_TEAM, teamId, root.getId(), configProperties.getTrashRetentionSeconds());
        }
        if (totalSize > 0) {
            teamQuotaService.decreaseUsedSpace(teamId, totalSize);
        }
    }

    private SysFile requireTeamTrashRoot(Long teamId, Long trashId) {
        SysFile file = sysFileMapper.selectTeamFile(teamId, trashId);
        if (file == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队回收站文件不存在");
        }
        if (!Integer.valueOf(1).equals(file.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "仅团队回收站中的文件可恢复");
        }
        if (!Integer.valueOf(1).equals(file.getRecycleRoot())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "请恢复对应的团队回收站根节点");
        }
        validateTrashUnexpired(file);
        return file;
    }

    private SysFile requireTeamTrashRootForPermanentDelete(Long teamId, Long trashId) {
        SysFile file = sysFileMapper.selectTeamFile(teamId, trashId);
        if (file == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队回收站文件不存在");
        }
        if (!Integer.valueOf(1).equals(file.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "仅团队回收站中的文件可永久删除");
        }
        if (!Integer.valueOf(1).equals(file.getRecycleRoot())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "请永久删除对应的团队回收站根节点");
        }
        return file;
    }

    private SysFile requireActivePersonalFile(Long userId, Long sourceFileId) {
        SysFile file = sysFileMapper.selectPersonalFile(userId, sourceFileId);
        if (file == null || Integer.valueOf(1).equals(file.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人文件不存在或不可转存");
        }
        return file;
    }

    private String validatePersonalTargetDirectory(Long userId, Long directoryId) {
        if (ROOT_ID == directoryId) {
            return "";
        }
        SysFile directory = sysFileMapper.selectPersonalFile(userId, directoryId);
        if (directory == null
                || !userId.equals(directory.getUserId())
                || !SPACE_TYPE_PERSONAL.equals(directory.getSpaceType())
                || !userId.equals(directory.getSpaceId())
                || !Integer.valueOf(1).equals(directory.getIsDirectory())
                || Integer.valueOf(1).equals(directory.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人目录不存在");
        }
        return directory.getFullPath();
    }

    private void validatePersonalTransferDescendants(Long userId, List<SysFile> descendants) {
        for (SysFile descendant : descendants) {
            if (!userId.equals(descendant.getUserId())
                    || !SPACE_TYPE_PERSONAL.equals(descendant.getSpaceType())
                    || !userId.equals(descendant.getSpaceId())
                    || Integer.valueOf(1).equals(descendant.getInRecycleBin())) {
                throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人文件不存在或不可转存");
            }
            validatePersonalTransferIsDirectory(descendant);
        }
    }

    private void validateTrashUnexpired(SysFile file) {
        LocalDateTime expireAt = file.getExpireAt();
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队回收站文件已过期");
        }
    }

    private RestoreTarget resolveRestoreTarget(Long teamId, SysFile root) {
        Long parentId = root.getParentId() == null ? 0L : root.getParentId();
        if (parentId == 0L) {
            return new RestoreTarget(0L, "");
        }
        SysFile parent = sysFileMapper.selectTeamFile(teamId, parentId);
        if (parent == null
                || !Integer.valueOf(1).equals(parent.getIsDirectory())
                || Integer.valueOf(1).equals(parent.getInRecycleBin())) {
            return new RestoreTarget(0L, "");
        }
        return new RestoreTarget(parent.getId(), parent.getFullPath());
    }

    private String resolveRestoreName(Long teamId, SysFile root, Long parentId, ConflictPolicy conflictPolicy) {
        SysFile conflict = sysFileMapper.selectTeamActiveByNameInDirectory(teamId, parentId, root.getOriginalName());
        if (conflict == null) {
            return root.getOriginalName();
        }
        if (conflictPolicy == null) {
            throw new RestoreNameConflictException(conflict);
        }
        if (ConflictPolicy.RENAME.equals(conflictPolicy)) {
            return generateUniqueTeamName(teamId, parentId, root.getOriginalName());
        }
        if (!ConflictPolicy.OVERWRITE.equals(conflictPolicy)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不支持的冲突处理策略");
        }
        moveActiveRootToTrash(teamId, conflict);
        return root.getOriginalName();
    }

    private void lockTeamSpaceForWrite(Long teamId) {
        if (teamSpaceMapper.lockActiveTeamSpace(teamId) == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在或已解散");
        }
    }

    private void rollbackUploadReservation(Long teamId, long fileSize, boolean quotaReserved,
                                           StoredFileObjectBO storedObject) {
        if (storedObject != null) {
            fileObjectService.decreaseReferenceOrRemove(storedObject);
        }
        if (quotaReserved) {
            teamQuotaService.decreaseUsedSpace(teamId, fileSize);
        }
    }

    private void lockTargetDirectory(String spaceType, Long spaceId, Long parentId) {
        sysFileMapper.lockActiveChildrenInSpace(spaceType, spaceId, parentId);
    }

    private void validateTeamNameUnique(Long teamId, Long parentId, String name) {
        if (sysFileMapper.existsTeamNameInDirectory(teamId, parentId, name)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录下已存在同名文件或目录");
        }
    }

    private SysFile copyTreeToTeam(SysFile source, List<SysFile> descendants, Long teamId, Long uploaderId,
                                   Long parentId, String parentFullPath, String rootName) {
        Map<Long, Long> idMap = new HashMap<>();
        Map<Long, String> pathMap = new HashMap<>();
        SysFile rootCopy = sysFileConverter.toClonedFile(
                new FileCloneBuildBO(source, uploaderId, SPACE_TYPE_TEAM, teamId, uploaderId, parentId, rootName));
        insertWithFullPath(rootCopy, parentFullPath);
        increaseReferenceIfFile(rootCopy);
        idMap.put(source.getId(), rootCopy.getId());
        pathMap.put(source.getId(), rootCopy.getFullPath());

        for (SysFile child : descendants) {
            Long newParentId = idMap.get(child.getParentId());
            String newParentPath = pathMap.get(child.getParentId());
            SysFile childCopy = sysFileConverter.toClonedFile(
                    new FileCloneBuildBO(child, uploaderId, SPACE_TYPE_TEAM, teamId,
                            uploaderId, newParentId, child.getOriginalName()));
            insertWithFullPath(childCopy, newParentPath);
            increaseReferenceIfFile(childCopy);
            idMap.put(child.getId(), childCopy.getId());
            pathMap.put(child.getId(), childCopy.getFullPath());
        }
        return rootCopy;
    }

    private SysFile copyTreeToPersonal(SysFile source, List<SysFile> descendants, Long userId,
                                       Long parentId, String parentFullPath, String rootName) {
        Map<Long, Long> idMap = new HashMap<>();
        Map<Long, String> pathMap = new HashMap<>();
        SysFile rootCopy = sysFileConverter.toClonedFile(
                new FileCloneBuildBO(source, userId, SPACE_TYPE_PERSONAL, userId, userId, parentId, rootName));
        insertWithFullPath(rootCopy, parentFullPath);
        increaseReferenceIfFile(rootCopy);
        idMap.put(source.getId(), rootCopy.getId());
        pathMap.put(source.getId(), rootCopy.getFullPath());

        for (SysFile child : descendants) {
            Long newParentId = idMap.get(child.getParentId());
            String newParentPath = pathMap.get(child.getParentId());
            SysFile childCopy = sysFileConverter.toClonedFile(
                    new FileCloneBuildBO(child, userId, SPACE_TYPE_PERSONAL, userId,
                            userId, newParentId, child.getOriginalName()));
            insertWithFullPath(childCopy, newParentPath);
            increaseReferenceIfFile(childCopy);
            idMap.put(child.getId(), childCopy.getId());
            pathMap.put(child.getId(), childCopy.getFullPath());
        }
        return rootCopy;
    }

    private void insertWithFullPath(SysFile file, String parentFullPath) {
        sysFileMapper.insert(file);
        file.setFullPath(buildChildFullPath(parentFullPath, file.getId()));
        sysFileMapper.updateById(file);
    }

    private long calculateLogicalSize(SysFile root, List<SysFile> descendants) {
        long total = fileSizeOf(root);
        for (SysFile child : descendants) {
            total += fileSizeOf(child);
        }
        return total;
    }

    private long checkPersonalTransferQuota(Long userId, SysFile root, List<SysFile> descendants) {
        checkPersonalSingleFileLimit(userId, root);
        for (SysFile child : descendants) {
            checkPersonalSingleFileLimit(userId, child);
        }
        long totalSize = calculateLogicalSize(root, descendants);
        if (totalSize > 0) {
            quotaService.checkQuota(userId, totalSize);
        }
        return totalSize;
    }

    private void checkPersonalSingleFileLimit(Long userId, SysFile file) {
        if (Integer.valueOf(0).equals(file.getIsDirectory())) {
            quotaService.checkSingleFileLimit(userId, fileSizeOf(file));
        }
    }

    private long fileSizeOf(SysFile file) {
        if (Integer.valueOf(1).equals(file.getIsDirectory())) {
            return 0L;
        }
        return file.getFileSize() == null ? 0L : file.getFileSize();
    }

    private void decreaseReferencesForDeletedTree(SysFile root, List<SysFile> descendants) {
        decreaseReferenceIfFile(root);
        for (SysFile child : descendants) {
            decreaseReferenceIfFile(child);
        }
    }

    private void decreaseReferenceIfFile(SysFile file) {
        if (Integer.valueOf(0).equals(file.getIsDirectory())) {
            fileObjectService.decreaseReferenceOrRemove(file);
        }
    }

    private List<SysFile> sortedActiveDescendants(String spaceType, Long spaceId, Long rootId) {
        List<SysFile> descendants = new ArrayList<>(
                sysFileMapper.selectActiveDescendantsInSpace(spaceType, spaceId, rootId));
        descendants.sort(Comparator.comparingInt(file -> StringUtils.parseIdList(file.getFullPath()).size()));
        return descendants;
    }

    private List<SysFile> sortedPersonalTransferDescendants(Long userId, SysFile source) {
        if (source.getId() == null) {
            throw malformedPersonalTransferTree();
        }
        Map<Long, SysFile> activePersonalTree = buildPersonalActiveTreeMap(userId);
        List<Long> sourceFullPath = parsePersonalTransferFullPath(source);
        validatePersonalTransferRoot(source, sourceFullPath, activePersonalTree);
        validatePersonalTransferIsDirectory(source);
        validatePersonalTransferFileHash(source);
        List<SysFile> descendants = Integer.valueOf(1).equals(source.getIsDirectory())
                ? new ArrayList<>(sysFileMapper.selectAllDescendants(source.getId()))
                : new ArrayList<>();
        descendants.removeIf(this::isRecycled);
        validatePersonalTransferDescendants(userId, descendants);
        validatePersonalTransferFileHashes(descendants);
        Map<Long, List<Long>> pathMap = validatePersonalTransferDescendantTree(
                source, sourceFullPath, descendants, activePersonalTree);
        descendants.sort(Comparator.comparingInt(file -> pathMap.get(file.getId()).size()));
        return descendants;
    }

    private List<SysFile> sortedTeamTransferDescendants(Long teamId, SysFile source) {
        if (source.getId() == null) {
            throw malformedTeamTransferTree();
        }
        List<Long> sourceFullPath = parseTeamTransferFullPath(source);
        validateTeamTransferRoot(source, sourceFullPath);
        validateTeamTransferIsDirectory(source);
        validateTeamTransferFileHash(source);
        Map<Long, SysFile> activeTeamTree = buildTeamActiveTreeMap(teamId);
        validateTeamTransferAncestors(sourceFullPath, activeTeamTree);
        List<SysFile> descendants = Integer.valueOf(1).equals(source.getIsDirectory())
                ? new ArrayList<>(sysFileMapper.selectActiveDescendantsInSpace(SPACE_TYPE_TEAM, teamId, source.getId()))
                : new ArrayList<>();
        validateTeamTransferDescendants(teamId, descendants);
        validateTeamTransferFileHashes(descendants);
        Map<Long, List<Long>> pathMap = validateTeamTransferDescendantTree(
                source, sourceFullPath, descendants, activeTeamTree);
        descendants.sort(Comparator.comparingInt(file -> pathMap.get(file.getId()).size()));
        return descendants;
    }

    private void validateTeamTransferDescendants(Long teamId, List<SysFile> descendants) {
        for (SysFile descendant : descendants) {
            if (!SPACE_TYPE_TEAM.equals(descendant.getSpaceType())
                    || !teamId.equals(descendant.getSpaceId())
                    || Integer.valueOf(1).equals(descendant.getInRecycleBin())) {
                throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队文件不存在");
            }
            validateTeamTransferIsDirectory(descendant);
        }
    }

    private void validateTeamTransferFileHashes(List<SysFile> descendants) {
        for (SysFile descendant : descendants) {
            validateTeamTransferFileHash(descendant);
        }
    }

    private void validateTeamTransferFileHash(SysFile file) {
        if (!Integer.valueOf(1).equals(file.getIsDirectory()) && StrUtil.isBlank(file.getFileHash())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "团队文件缺少文件指纹，无法转存");
        }
    }

    private Map<Long, List<Long>> validateTeamTransferDescendantTree(SysFile source, List<Long> sourceFullPath,
                                                                     List<SysFile> descendants,
                                                                     Map<Long, SysFile> activeTeamTree) {
        Map<Long, List<Long>> pathMap = new HashMap<>();
        Set<Long> descendantIds = new HashSet<>();
        Long sourceId = source.getId();
        if (sourceId == null) {
            throw malformedTeamTransferTree();
        }
        for (SysFile descendant : descendants) {
            if (descendant.getId() == null || descendant.getParentId() == null
                    || sourceId.equals(descendant.getId()) || !descendantIds.add(descendant.getId())) {
                throw malformedTeamTransferTree();
            }
            List<Long> fullPath = parseTeamTransferFullPath(descendant);
            int lastIndex = fullPath.size() - 1;
            if (lastIndex < sourceFullPath.size()
                    || !fullPath.subList(0, sourceFullPath.size()).equals(sourceFullPath)
                    || !descendant.getId().equals(fullPath.get(lastIndex))
                    || !descendant.getParentId().equals(fullPath.get(lastIndex - 1))) {
                throw malformedTeamTransferTree();
            }
            pathMap.put(descendant.getId(), fullPath);
        }

        List<SysFile> orderedDescendants = new ArrayList<>(descendants);
        orderedDescendants.sort(Comparator.comparingInt(file -> pathMap.get(file.getId()).size()));
        Set<Long> mappedParentIds = new HashSet<>();
        mappedParentIds.add(sourceId);
        for (SysFile descendant : orderedDescendants) {
            if (!mappedParentIds.contains(descendant.getParentId())) {
                throw malformedTeamTransferTree();
            }
            if (!sourceId.equals(descendant.getParentId())) {
                List<Long> fullPath = pathMap.get(descendant.getId());
                List<Long> parentFullPath = pathMap.get(descendant.getParentId());
                if (parentFullPath == null
                        || !fullPath.subList(0, fullPath.size() - 1).equals(parentFullPath)) {
                    throw malformedTeamTransferTree();
                }
            }
            mappedParentIds.add(descendant.getId());
        }
        validateNoFullPathOnlyTeamOrphans(activeTeamTree.values(), sourceId, sourceFullPath, descendantIds);
        return pathMap;
    }

    private Map<Long, SysFile> buildTeamActiveTreeMap(Long teamId) {
        Map<Long, SysFile> activeTeamTree = new HashMap<>();
        for (SysFile file : sysFileMapper.selectTeamActiveTree(teamId)) {
            if (isRecycled(file)) {
                continue;
            }
            if (file.getId() == null || activeTeamTree.put(file.getId(), file) != null) {
                throw malformedTeamTransferTree();
            }
        }
        return activeTeamTree;
    }

    private void validateTeamTransferRoot(SysFile source, List<Long> sourceFullPath) {
        if (sourceFullPath.isEmpty() || !source.getId().equals(sourceFullPath.get(sourceFullPath.size() - 1))) {
            throw malformedTeamTransferTree();
        }
        validateTeamParentMatchesFullPath(source, sourceFullPath);
    }

    private void validateTeamTransferAncestors(List<Long> sourceFullPath, Map<Long, SysFile> activeTeamTree) {
        for (int index = 0; index < sourceFullPath.size() - 1; index++) {
            Long ancestorId = sourceFullPath.get(index);
            SysFile ancestor = activeTeamTree.get(ancestorId);
            if (ancestor == null || !Integer.valueOf(1).equals(ancestor.getIsDirectory())) {
                throw malformedTeamTransferTree();
            }
            List<Long> ancestorFullPath = parseTeamTransferFullPath(ancestor);
            if (!ancestorFullPath.equals(sourceFullPath.subList(0, index + 1))) {
                throw malformedTeamTransferTree();
            }
            validateTeamParentMatchesFullPath(ancestor, ancestorFullPath);
        }
    }

    private void validateTeamParentMatchesFullPath(SysFile file, List<Long> fullPath) {
        Long parentId = file.getParentId();
        if (fullPath.size() == 1) {
            if (parentId != null && !Long.valueOf(0L).equals(parentId)) {
                throw malformedTeamTransferTree();
            }
            return;
        }
        if (!fullPath.get(fullPath.size() - 2).equals(parentId)) {
            throw malformedTeamTransferTree();
        }
    }

    private List<Long> parseTeamTransferFullPath(SysFile file) {
        try {
            List<Long> fullPath = StringUtils.parseIdList(file.getFullPath());
            if (fullPath.isEmpty()) {
                throw malformedTeamTransferTree();
            }
            return fullPath;
        } catch (NumberFormatException e) {
            throw malformedTeamTransferTree();
        }
    }

    private void validateTeamTransferIsDirectory(SysFile file) {
        if (!Integer.valueOf(0).equals(file.getIsDirectory())
                && !Integer.valueOf(1).equals(file.getIsDirectory())) {
            throw malformedTeamTransferTree();
        }
    }

    private void validateNoFullPathOnlyTeamOrphans(Iterable<SysFile> activeTeamTree, Long sourceId,
                                                   List<Long> sourceFullPath, Set<Long> descendantIds) {
        for (SysFile file : activeTeamTree) {
            if (sourceId.equals(file.getId()) || descendantIds.contains(file.getId())) {
                continue;
            }
            if (sourceId.equals(file.getParentId())) {
                throw malformedTeamTransferTree();
            }
            List<Long> fullPath = parseTeamActiveTreeFullPath(file, sourceFullPath);
            if (fullPath.size() > sourceFullPath.size()
                    && fullPath.subList(0, sourceFullPath.size()).equals(sourceFullPath)) {
                throw malformedTeamTransferTree();
            }
        }
    }

    private List<Long> parseTeamActiveTreeFullPath(SysFile file, List<Long> sourceFullPath) {
        try {
            return StringUtils.parseIdList(file.getFullPath());
        } catch (NumberFormatException e) {
            if (hasRawSourcePathPrefix(file.getFullPath(), sourceFullPath)) {
                throw malformedTeamTransferTree();
            }
            return List.of();
        }
    }

    private BusinessException malformedTeamTransferTree() {
        return new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "团队目录结构异常，无法转存");
    }

    private void validatePersonalTransferFileHashes(List<SysFile> descendants) {
        for (SysFile descendant : descendants) {
            validatePersonalTransferFileHash(descendant);
        }
    }

    private void validatePersonalTransferFileHash(SysFile file) {
        if (!Integer.valueOf(1).equals(file.getIsDirectory()) && StrUtil.isBlank(file.getFileHash())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "个人文件缺少文件指纹，无法转存");
        }
    }

    private Map<Long, List<Long>> validatePersonalTransferDescendantTree(SysFile source, List<Long> sourceFullPath,
                                                                         List<SysFile> descendants,
                                                                         Map<Long, SysFile> activePersonalTree) {
        Map<Long, List<Long>> pathMap = new HashMap<>();
        Set<Long> descendantIds = new HashSet<>();
        Long sourceId = source.getId();
        if (sourceId == null) {
            throw malformedPersonalTransferTree();
        }
        for (SysFile descendant : descendants) {
            if (descendant.getId() == null || descendant.getParentId() == null
                    || sourceId.equals(descendant.getId()) || !descendantIds.add(descendant.getId())) {
                throw malformedPersonalTransferTree();
            }
            List<Long> fullPath = parsePersonalTransferFullPath(descendant);
            int lastIndex = fullPath.size() - 1;
            if (lastIndex < sourceFullPath.size()
                    || !fullPath.subList(0, sourceFullPath.size()).equals(sourceFullPath)
                    || !descendant.getId().equals(fullPath.get(lastIndex))
                    || !descendant.getParentId().equals(fullPath.get(lastIndex - 1))) {
                throw malformedPersonalTransferTree();
            }
            pathMap.put(descendant.getId(), fullPath);
        }

        List<SysFile> orderedDescendants = new ArrayList<>(descendants);
        orderedDescendants.sort(Comparator.comparingInt(file -> pathMap.get(file.getId()).size()));
        Set<Long> mappedParentIds = new HashSet<>();
        mappedParentIds.add(sourceId);
        for (SysFile descendant : orderedDescendants) {
            if (!mappedParentIds.contains(descendant.getParentId())) {
                throw malformedPersonalTransferTree();
            }
            if (!sourceId.equals(descendant.getParentId())) {
                List<Long> fullPath = pathMap.get(descendant.getId());
                List<Long> parentFullPath = pathMap.get(descendant.getParentId());
                if (parentFullPath == null
                        || !fullPath.subList(0, fullPath.size() - 1).equals(parentFullPath)) {
                    throw malformedPersonalTransferTree();
                }
            }
            mappedParentIds.add(descendant.getId());
        }
        validateNoFullPathOnlyPersonalOrphans(activePersonalTree.values(), sourceId, sourceFullPath, descendantIds);
        return pathMap;
    }

    private Map<Long, SysFile> buildPersonalActiveTreeMap(Long userId) {
        Map<Long, SysFile> activePersonalTree = new HashMap<>();
        for (SysFile file : sysFileMapper.selectPersonalActiveTree(userId)) {
            if (isRecycled(file)) {
                continue;
            }
            if (file.getId() == null || activePersonalTree.put(file.getId(), file) != null) {
                throw malformedPersonalTransferTree();
            }
        }
        return activePersonalTree;
    }

    private void validatePersonalTransferRoot(SysFile source, List<Long> sourceFullPath,
                                              Map<Long, SysFile> activePersonalTree) {
        if (sourceFullPath.isEmpty() || !source.getId().equals(sourceFullPath.get(sourceFullPath.size() - 1))) {
            throw malformedPersonalTransferTree();
        }
        validateParentMatchesFullPath(source, sourceFullPath);
        for (int index = 0; index < sourceFullPath.size() - 1; index++) {
            Long ancestorId = sourceFullPath.get(index);
            SysFile ancestor = activePersonalTree.get(ancestorId);
            if (ancestor == null || !Integer.valueOf(1).equals(ancestor.getIsDirectory())) {
                throw malformedPersonalTransferTree();
            }
            List<Long> ancestorFullPath = parsePersonalTransferFullPath(ancestor);
            if (!ancestorFullPath.equals(sourceFullPath.subList(0, index + 1))) {
                throw malformedPersonalTransferTree();
            }
            validateParentMatchesFullPath(ancestor, ancestorFullPath);
        }
    }

    private void validateParentMatchesFullPath(SysFile file, List<Long> fullPath) {
        Long parentId = file.getParentId();
        if (fullPath.size() == 1) {
            if (parentId != null && !Long.valueOf(0L).equals(parentId)) {
                throw malformedPersonalTransferTree();
            }
            return;
        }
        if (!fullPath.get(fullPath.size() - 2).equals(parentId)) {
            throw malformedPersonalTransferTree();
        }
    }

    private void validateNoFullPathOnlyPersonalOrphans(Iterable<SysFile> activePersonalTree, Long sourceId,
                                                       List<Long> sourceFullPath, Set<Long> descendantIds) {
        for (SysFile file : activePersonalTree) {
            if (sourceId.equals(file.getId()) || descendantIds.contains(file.getId())) {
                continue;
            }
            if (sourceId.equals(file.getParentId())) {
                throw malformedPersonalTransferTree();
            }
            List<Long> fullPath = parsePersonalActiveTreeFullPath(file, sourceFullPath);
            if (fullPath.size() > sourceFullPath.size()
                    && fullPath.subList(0, sourceFullPath.size()).equals(sourceFullPath)) {
                throw malformedPersonalTransferTree();
            }
        }
    }

    private List<Long> parsePersonalTransferFullPath(SysFile descendant) {
        try {
            List<Long> fullPath = StringUtils.parseIdList(descendant.getFullPath());
            if (fullPath.isEmpty()) {
                throw malformedPersonalTransferTree();
            }
            return fullPath;
        } catch (NumberFormatException e) {
            throw malformedPersonalTransferTree();
        }
    }

    private void validatePersonalTransferIsDirectory(SysFile file) {
        if (!Integer.valueOf(0).equals(file.getIsDirectory())
                && !Integer.valueOf(1).equals(file.getIsDirectory())) {
            throw malformedPersonalTransferTree();
        }
    }

    private List<Long> parsePersonalActiveTreeFullPath(SysFile file, List<Long> sourceFullPath) {
        try {
            return StringUtils.parseIdList(file.getFullPath());
        } catch (NumberFormatException e) {
            if (hasRawSourcePathPrefix(file.getFullPath(), sourceFullPath)) {
                throw malformedPersonalTransferTree();
            }
            return List.of();
        }
    }

    private boolean hasRawSourcePathPrefix(String fullPath, List<Long> sourceFullPath) {
        if (fullPath == null) {
            return false;
        }
        String[] rawIds = fullPath.split(",");
        if (rawIds.length <= sourceFullPath.size()) {
            return false;
        }
        for (int index = 0; index < sourceFullPath.size(); index++) {
            if (!String.valueOf(sourceFullPath.get(index)).equals(rawIds[index].trim())) {
                return false;
            }
        }
        return true;
    }

    private BusinessException malformedPersonalTransferTree() {
        return new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "个人目录结构异常，无法转存");
    }

    private boolean isRecycled(SysFile file) {
        return Integer.valueOf(1).equals(file.getInRecycleBin());
    }

    private void increaseReferenceIfFile(SysFile file) {
        if (Integer.valueOf(0).equals(file.getIsDirectory()) && StrUtil.isNotBlank(file.getFileHash())) {
            fileObjectService.increaseReference(file.getFileHash());
        }
    }

    private String resolveUploadName(MultipartFile file) {
        if (file == null || StrUtil.isBlank(file.getOriginalFilename())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件名不能为空");
        }
        return file.getOriginalFilename();
    }

    private String generateUniqueTeamName(Long teamId, Long parentId, String originalName) {
        if (!sysFileMapper.existsTeamNameInDirectory(teamId, parentId, originalName)) {
            return originalName;
        }
        return nextAvailableName(originalName, sysFileMapper.selectTeamNamesInDirectory(
                teamId, parentId, FileUtil.mainName(originalName)));
    }

    private String generateUniquePersonalName(Long userId, Long parentId, String originalName) {
        if (!sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PERSONAL, userId, parentId, originalName)) {
            return originalName;
        }
        return nextAvailableName(originalName, sysFileMapper.selectNamesInSpaceDirectory(
                SPACE_TYPE_PERSONAL, userId, parentId, FileUtil.mainName(originalName)));
    }

    private String nextAvailableName(String originalName, List<String> existingNames) {
        String nameWithoutExt = FileUtil.mainName(originalName);
        String ext = FileUtil.getSuffix(originalName);
        String suffix = StrUtil.isNotBlank(ext) ? "." + ext : "";
        String prefix = nameWithoutExt + "(";
        String indexedSuffix = ")" + suffix;
        int maxIndex = 0;
        for (String name : existingNames) {
            if (name.startsWith(prefix) && name.endsWith(indexedSuffix)) {
                maxIndex = Math.max(maxIndex, parseCopyIndex(name, prefix, indexedSuffix));
            }
        }
        return nameWithoutExt + "(" + (maxIndex + 1) + ")" + suffix;
    }

    private int parseCopyIndex(String name, String prefix, String suffix) {
        String numPart = name.substring(prefix.length(), name.length() - suffix.length());
        try {
            return Integer.parseInt(numPart);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isDescendantPath(Long directoryId, String targetFullPath) {
        return StringUtils.parseIdList(targetFullPath).contains(directoryId);
    }

    private String buildChildFullPath(String parentFullPath, Long currentId) {
        return StrUtil.isBlank(parentFullPath) ? String.valueOf(currentId) : parentFullPath + "," + currentId;
    }

    private record RestoreTarget(Long parentId, String parentFullPath) {
    }

    /**
     * 团队回收站恢复同名冲突异常。
     */
    @Getter
    static class RestoreNameConflictException extends BusinessException {

        private final SysFile conflictFile;

        RestoreNameConflictException(SysFile conflictFile) {
            super(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录下已存在同名文件或目录");
            this.conflictFile = conflictFile;
        }
    }
}
