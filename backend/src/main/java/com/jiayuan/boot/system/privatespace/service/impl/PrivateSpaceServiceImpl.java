package com.jiayuan.boot.system.privatespace.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.oss.config.TrashRetentionProperties;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.bo.PrivateFileBuildBO;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.privatespace.converter.PrivateSpaceConverter;
import com.jiayuan.boot.system.privatespace.mapper.PrivateSpaceMapper;
import com.jiayuan.boot.system.privatespace.model.entity.PrivateSpace;
import com.jiayuan.boot.system.privatespace.model.enums.PrivateSpaceState;
import com.jiayuan.boot.system.privatespace.model.vo.PrivatePasswordRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSpaceStatusResponseVO;
import com.jiayuan.boot.system.privatespace.service.PrivateSpaceService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.JwtTokenUtils;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 私密空间管理服务实现。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Service
@RequiredArgsConstructor
public class PrivateSpaceServiceImpl implements PrivateSpaceService {

    private static final Duration UNLOCK_TTL = Duration.ofMinutes(3);
    private static final String SESSION_KEY_FORMAT = "private-space:session:%d:%s";
    private static final String SESSION_VALUE = "1";
    private static final String SPACE_TYPE_PRIVATE = "PRIVATE";
    private static final long ROOT_ID = 0L;
    private final PrivateSpaceMapper privateSpaceMapper;
    private final QuotaService quotaService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final PrivateSpaceConverter privateSpaceConverter;
    private final SysFileMapper sysFileMapper;
    private final SysFileConverter sysFileConverter;
    private final SysUserMapper sysUserMapper;
    private final FileObjectService fileObjectService;
    private final TrashRetentionProperties trashRetentionProperties;
    private final SystemConfigProperties systemConfigProperties;

    /**
     * 查询当前用户私密空间状态。
     */
    @Override
    public PrivateSpaceStatusResponseVO getStatus() {
        Long userId = SecurityUtils.getCurrentUserId();
        PrivateSpace privateSpace = privateSpaceMapper.selectByUserId(userId);
        LocalDateTime unlockedUntil = getUnlockedUntil(userId);
        PrivateSpaceState state = resolveState(userId, privateSpace, unlockedUntil);
        String reminder = buildReminder(state, privateSpace);
        LocalDateTime graceExpireAt = privateSpace == null ? null : privateSpace.getGraceExpireAt();
        return privateSpaceConverter.toStatusResponseVO(state, unlockedUntil, graceExpireAt, reminder);
    }

    /**
     * 设置或修改当前用户私密空间密码。
     */
    @Override
    @Transactional
    public void updatePassword(PrivatePasswordRequestVO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireVip(userId);
        PrivateSpace privateSpace = privateSpaceMapper.selectByUserId(userId);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        if (privateSpace == null) {
            privateSpaceMapper.insert(privateSpaceConverter.toPrivateSpace(userId, encodedPassword));
            return;
        }
        requireOldPassword(privateSpace, request.getOldPassword());
        privateSpace.setPasswordHash(encodedPassword);
        privateSpaceMapper.updateById(privateSpace);
        deleteUnlockSession(userId);
    }

    /**
     * 校验私密空间密码并创建解锁会话。
     */
    @Override
    public PrivateSessionResponseVO unlock(PrivateSessionRequestVO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireVip(userId);
        PrivateSpace privateSpace = requireEnabledPrivateSpace(userId);
        if (!passwordEncoder.matches(request.getPassword(), privateSpace.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR, "私密空间密码错误");
        }
        LocalDateTime unlockedUntil = saveUnlockSession(userId);
        return privateSpaceConverter.toSessionResponseVO(unlockedUntil);
    }

    /**
     * 列出私密空间目录。
     */
    @Override
    public List<DirectoryNodeResponseVO> listDirectories(Long parentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireReadablePrivateSpace(userId);
        Long resolvedParentId = resolveParentId(parentId);
        validateTargetDirectory(userId, resolvedParentId);

        List<SysFile> children = sysFileMapper.selectDirectoriesInSpace(
                SPACE_TYPE_PRIVATE, userId, resolvedParentId);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> childIds = children.stream().map(SysFile::getId).toList();
        Set<Long> idsHavingChildren = new HashSet<>(
                sysFileMapper.selectParentIdsHavingChildDirectoryInSpace(
                        SPACE_TYPE_PRIVATE, userId, childIds));
        return children.stream()
                .map(child -> sysFileConverter.toDirectoryNodeVO(child, idsHavingChildren.contains(child.getId())))
                .toList();
    }

    /**
     * 新建私密空间目录。
     */
    @Override
    @Transactional
    public FileInfoResponseVO createDirectory(DirectoryCreateRequestVO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        Long parentId = resolveParentId(request.getParentId());
        String parentFullPath = validateTargetDirectory(userId, parentId);
        lockTargetDirectory(userId, parentId);
        validateNameUnique(userId, parentId, request.getName());

        SysFile directory = sysFileConverter.toPrivateDirectory(
                new PrivateFileBuildBO(null, userId, parentId, request.getName()));
        insertWithFullPath(directory, parentFullPath);
        return sysFileConverter.toFileInfoVO(directory);
    }

    /**
     * 列出私密空间文件和目录。
     */
    @Override
    public FileListResponseVO listFiles(Long parentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireReadablePrivateSpace(userId);
        Long resolvedParentId = resolveParentId(parentId);
        validateTargetDirectory(userId, resolvedParentId);

        List<SysFile> files = sysFileMapper.selectChildrenInSpace(SPACE_TYPE_PRIVATE, userId, resolvedParentId);
        List<FileInfoResponseVO> items = sysFileConverter.toFileInfoVOList(files);
        return sysFileConverter.toFileListResponseVO(items, buildBreadcrumb(userId, resolvedParentId));
    }

    /**
     * 上传私密空间文件。
     */
    @Override
    @Transactional
    public FileInfoResponseVO uploadFile(MultipartFile file, Long parentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        Long resolvedParentId = resolveParentId(parentId);
        String parentFullPath = validateTargetDirectory(userId, resolvedParentId);
        String originalName = resolveUploadName(file);

        long fileSize = file.getSize();
        quotaService.checkSingleFileLimit(userId, fileSize);
        quotaService.checkQuota(userId, fileSize);
        lockTargetDirectory(userId, resolvedParentId);

        String uniqueName = generateUniquePrivateName(userId, resolvedParentId, originalName);
        StoredFileObjectBO storedObject = null;
        SysFile privateFile = null;
        boolean quotaReserved = fileSize > 0;
        if (quotaReserved) {
            quotaService.increaseUsedSpace(userId, fileSize);
        }
        try {
            storedObject = fileObjectService.saveOrReuse(file);
            privateFile = sysFileConverter.toPrivateUploadedFile(
                    new PrivateFileBuildBO(storedObject, userId, resolvedParentId, uniqueName));
            insertWithFullPath(privateFile, parentFullPath);
            return sysFileConverter.toFileInfoVO(privateFile);
        } catch (RuntimeException e) {
            rollbackUploadReservation(userId, fileSize, quotaReserved, storedObject, privateFile);
            throw e;
        }
    }

    /**
     * 获取私密空间文件或目录详情。
     */
    @Override
    public FileInfoResponseVO getFile(Long fileId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireReadablePrivateSpace(userId);
        return sysFileConverter.toFileInfoVO(requireActivePrivateFile(userId, fileId));
    }

    /**
     * 下载私密空间文件。
     */
    @Override
    public void downloadFile(Long fileId, HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireReadablePrivateSpace(userId);
        SysFile file = requireActivePrivateFile(userId, fileId);
        if (isDirectory(file)) {
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "不支持下载目录");
        }
        fileObjectService.writeToResponse(file, response);
    }

    /**
     * 移动私密空间文件或目录。
     */
    @Override
    @Transactional
    public void moveFile(Long fileId, Long targetDirectoryId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        Long resolvedTargetId = resolveParentId(targetDirectoryId);
        SysFile source = requireActivePrivateFile(userId, fileId);
        if (source.getParentId().equals(resolvedTargetId)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件已在该目录下");
        }

        String targetFullPath = validateTargetDirectory(userId, resolvedTargetId);
        if (isDirectory(source) && isDescendantPath(source.getId(), targetFullPath)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将目录移动到自身或子目录下");
        }
        lockTargetDirectory(userId, resolvedTargetId);
        validateNameUnique(userId, resolvedTargetId, source.getOriginalName());

        String newFullPath = buildChildFullPath(targetFullPath, source.getId());
        sysFileMapper.updateRootLocationInSpace(
                SPACE_TYPE_PRIVATE, userId, source.getId(), resolvedTargetId, newFullPath);
        if (isDirectory(source)) {
            sysFileMapper.updateDescendantsFullPathInSpace(
                    SPACE_TYPE_PRIVATE, userId, source.getId(), newFullPath);
        }
    }

    /**
     * 删除私密空间文件或目录到私密回收站。
     */
    @Override
    @Transactional
    public void deleteToTrash(Long fileId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        SysFile root = requireActivePrivateFile(userId, fileId);
        moveActiveRootToTrash(userId, root);
    }

    /**
     * 列出私密空间回收站根节点。
     */
    @Override
    public List<RecycleBinItemResponseVO> listTrash() {
        Long userId = SecurityUtils.getCurrentUserId();
        requireReadablePrivateSpace(userId);
        List<SysFile> files = sysFileMapper.selectTrashRootsInSpace(SPACE_TYPE_PRIVATE, userId);
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> idToName = resolveTrashAncestorNames(userId, files);
        Set<Long> deleterIds = files.stream()
                .map(SysFile::getDeletedBy)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, UserBriefBO> deleterMap = deleterIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectUserBriefByIds(new ArrayList<>(deleterIds)).stream()
                        .collect(Collectors.toMap(UserBriefBO::getUserId, u -> u, (a, b) -> a));
        return files.stream()
                .map(file -> toTrashItem(file, idToName, deleterMap))
                .toList();
    }

    /**
     * 恢复私密空间回收站根节点。
     */
    @Override
    @Transactional
    public FileInfoResponseVO restoreTrash(Long trashId, ConflictPolicy conflictPolicy) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        SysFile root = requirePrivateTrashRoot(userId, trashId);
        RestoreTarget target = resolveRestoreTarget(userId, root);
        List<SysFile> descendants = isDirectory(root)
                ? trashTreeDescendants(userId, root.getId())
                : List.of();
        long totalSize = calculateLogicalSize(root, descendants);

        lockTargetDirectory(userId, target.parentId());
        String restoreName = resolveRestoreName(userId, root, target.parentId(), conflictPolicy);
        if (totalSize > 0) {
            quotaService.checkQuota(userId, totalSize);
        }

        int restored = sysFileMapper.restoreTrashTreeInSpace(
                SPACE_TYPE_PRIVATE, userId, root.getId(), target.parentId(), target.parentFullPath(), restoreName);
        if (restored == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密回收站文件状态已变化");
        }
        if (totalSize > 0) {
            quotaService.increaseUsedSpace(userId, totalSize);
        }
        return sysFileConverter.toFileInfoVO(sysFileMapper.selectSpaceFile(SPACE_TYPE_PRIVATE, userId, root.getId()));
    }

    /**
     * 永久删除私密空间回收站根节点。
     */
    @Override
    @Transactional
    public void permanentlyDeleteTrash(Long trashId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWritablePrivateSpace(userId);
        SysFile root = requirePrivateTrashRootForPermanentDelete(userId, trashId);
        List<SysFile> descendants = isDirectory(root)
                ? trashTreeDescendants(userId, root.getId())
                : List.of();
        int deleted = sysFileMapper.permanentlyDeleteTrashTreeInSpace(SPACE_TYPE_PRIVATE, userId, root.getId());
        if (deleted == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密回收站文件状态已变化");
        }
        decreaseReferencesForDeletedTree(root, descendants);
    }

    /**
     * 处理用户 VIP 状态变化后的私密空间生命周期。
     */
    @Override
    @Transactional
    public void handleVipStateChanged(Long userId, boolean vip) {
        PrivateSpace privateSpace = privateSpaceMapper.selectByUserId(userId);
        if (privateSpace == null || StrUtil.isBlank(privateSpace.getPasswordHash())) {
            return;
        }
        LocalDateTime graceExpireAt = vip
                ? null
                : LocalDateTime.now().plus(Duration.ofSeconds(systemConfigProperties.getPrivateGracePeriodSeconds()));
        privateSpaceMapper.updateGraceExpireAt(userId, graceExpireAt);
    }

    /**
     * 判断私密空间状态。
     */
    private PrivateSpaceState resolveState(Long userId, PrivateSpace privateSpace, LocalDateTime unlockedUntil) {
        if (privateSpace == null || StrUtil.isBlank(privateSpace.getPasswordHash())) {
            return PrivateSpaceState.DISABLED;
        }
        if (quotaService.isVip(userId)) {
            return unlockedUntil == null ? PrivateSpaceState.LOCKED : PrivateSpaceState.ACTIVE;
        }
        LocalDateTime graceExpireAt = privateSpace.getGraceExpireAt();
        if (graceExpireAt != null && graceExpireAt.isAfter(LocalDateTime.now())) {
            return PrivateSpaceState.GRACE_PERIOD;
        }
        return PrivateSpaceState.EXPIRED;
    }

    /**
     * 校验私密空间可读。
     */
    private void requireReadablePrivateSpace(Long userId) {
        PrivateSpaceState state = currentState(userId);
        if (state == PrivateSpaceState.ACTIVE || state == PrivateSpaceState.GRACE_PERIOD) {
            return;
        }
        throw privateSpaceAccessException(state);
    }

    /**
     * 校验私密空间可写。
     */
    private void requireWritablePrivateSpace(Long userId) {
        PrivateSpaceState state = currentState(userId);
        if (state == PrivateSpaceState.ACTIVE) {
            return;
        }
        throw privateSpaceAccessException(state);
    }

    /**
     * 查询当前私密空间状态。
     */
    private PrivateSpaceState currentState(Long userId) {
        PrivateSpace privateSpace = privateSpaceMapper.selectByUserId(userId);
        return resolveState(userId, privateSpace, getUnlockedUntil(userId));
    }

    /**
     * 构造私密空间访问异常。
     */
    private BusinessException privateSpaceAccessException(PrivateSpaceState state) {
        if (state == PrivateSpaceState.DISABLED) {
            return new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "请先设置私密空间密码");
        }
        if (state == PrivateSpaceState.LOCKED) {
            return new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "请先解锁私密空间");
        }
        return new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "私密空间当前状态不允许该操作");
    }

    /**
     * 解析父目录 ID。
     */
    private Long resolveParentId(Long parentId) {
        return parentId == null ? ROOT_ID : parentId;
    }

    /**
     * 校验私密空间目标目录并返回 fullPath。
     */
    private String validateTargetDirectory(Long userId, Long parentId) {
        if (ROOT_ID == parentId) {
            return "";
        }
        SysFile parent = requireActivePrivateFile(userId, parentId);
        if (!isDirectory(parent)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录不存在");
        }
        return StrUtil.blankToDefault(parent.getFullPath(), String.valueOf(parent.getId()));
    }

    /**
     * 查询私密空间活动文件。
     */
    private SysFile requireActivePrivateFile(Long userId, Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件ID非法");
        }
        SysFile file = sysFileMapper.selectSpaceFile(SPACE_TYPE_PRIVATE, userId, fileId);
        if (file == null || isInTrash(file)) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密空间文件不存在");
        }
        return file;
    }

    /**
     * 校验同目录名称唯一。
     */
    private void validateNameUnique(Long userId, Long parentId, String name) {
        if (sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PRIVATE, userId, parentId, name)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "同名文件或目录已存在");
        }
    }

    /**
     * 锁定目标目录下活动子节点，降低同名并发冲突。
     */
    private void lockTargetDirectory(Long userId, Long parentId) {
        sysFileMapper.lockActiveChildrenInSpace(SPACE_TYPE_PRIVATE, userId, parentId);
    }

    /**
     * 移入私密回收站并释放逻辑容量。
     */
    private void moveActiveRootToTrash(Long userId, SysFile root) {
        List<SysFile> descendants = isDirectory(root)
                ? sysFileMapper.selectActiveDescendantsInSpace(SPACE_TYPE_PRIVATE, userId, root.getId())
                : List.of();
        long totalSize = calculateLogicalSize(root, descendants);
        int moved = sysFileMapper.moveRootToTrashInSpace(
                SPACE_TYPE_PRIVATE, userId, root.getId(), userId,
                systemConfigProperties.getTrashRetentionSeconds());
        if (moved == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密空间文件状态已变化");
        }
        if (isDirectory(root)) {
            sysFileMapper.updateDescendantsRecycleStateInSpace(SPACE_TYPE_PRIVATE, userId, root.getId(), systemConfigProperties.getTrashRetentionSeconds());
        }
        if (totalSize > 0) {
            quotaService.decreaseUsedSpace(userId, totalSize);
        }
    }

    /**
     * 插入后补齐 ID 路径。
     */
    private void insertWithFullPath(SysFile file, String parentFullPath) {
        sysFileMapper.insert(file);
        file.setFullPath(buildChildFullPath(parentFullPath, file.getId()));
        sysFileMapper.updateById(file);
    }

    /**
     * 构造子节点 fullPath。
     */
    private String buildChildFullPath(String parentFullPath, Long fileId) {
        return StrUtil.isBlank(parentFullPath)
                ? String.valueOf(fileId)
                : parentFullPath + "," + fileId;
    }

    /**
     * 构造列表面包屑。
     */
    private List<BreadcrumbItemResponseVO> buildBreadcrumb(Long userId, Long parentId) {
        List<BreadcrumbItemResponseVO> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItemResponseVO(ROOT_ID, "根目录"));
        if (ROOT_ID == parentId) {
            return crumbs;
        }

        SysFile parent = requireActivePrivateFile(userId, parentId);
        List<Long> chain = StringUtils.parseIdList(parent.getFullPath());
        if (chain.isEmpty()) {
            return crumbs;
        }

        Map<Long, String> idToName = sysFileMapper.selectFilesInSpaceByIds(
                        SPACE_TYPE_PRIVATE, userId, chain).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (a, b) -> a));
        for (Long id : chain) {
            crumbs.add(new BreadcrumbItemResponseVO(id, idToName.getOrDefault(id, "?")));
        }
        return crumbs;
    }

    /**
     * 查询私密回收站路径中的祖先名称。
     */
    private Map<Long, String> resolveTrashAncestorNames(Long userId, List<SysFile> files) {
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
        return sysFileMapper.selectFilesInSpaceByIds(SPACE_TYPE_PRIVATE, userId, ancestorIds).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (left, right) -> left));
    }

    /**
     * 转换私密回收站列表项。
     */
    private RecycleBinItemResponseVO toTrashItem(SysFile file, Map<Long, String> idToName,
                                                   Map<Long, UserBriefBO> deleterMap) {
        RecycleBinItemResponseVO vo = sysFileConverter.toRecycleBinItemVO(file, buildTrashPath(file, idToName));
        if (file.getDeletedBy() != null) {
            vo.setDeletedByUserId(file.getDeletedBy());
            UserBriefBO deleter = deleterMap.get(file.getDeletedBy());
            if (deleter != null) {
                vo.setDeletedByAccountId(deleter.getAccountId());
                vo.setDeletedByAccountName(deleter.getAccountName());
                vo.setDeletedByName(deleter.getNickname());
            } else {
                vo.setDeletedByName("未知");
            }
        }
        vo.setExpireAt(file.getExpireAt());
        return vo;
    }

    /**
     * 构造私密回收站展示路径。
     */
    private String buildTrashPath(SysFile file, Map<Long, String> idToName) {
        List<Long> chain = StringUtils.parseIdList(file.getFullPath());
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < chain.size() - 1; index++) {
            path.append('/').append(idToName.getOrDefault(chain.get(index), "?"));
        }
        path.append('/').append(file.getOriginalName());
        return path.toString();
    }

    /**
     * 查询可恢复的私密回收站根节点。
     */
    private SysFile requirePrivateTrashRoot(Long userId, Long trashId) {
        SysFile file = requirePrivateTrashRootForPermanentDelete(userId, trashId);
        validateTrashUnexpired(file);
        return file;
    }

    /**
     * 查询可永久删除的私密回收站根节点。
     */
    private SysFile requirePrivateTrashRootForPermanentDelete(Long userId, Long trashId) {
        SysFile file = sysFileMapper.selectSpaceFile(SPACE_TYPE_PRIVATE, userId, trashId);
        if (file == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密回收站文件不存在");
        }
        if (!Integer.valueOf(1).equals(file.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "仅私密回收站中的文件可操作");
        }
        if (!Integer.valueOf(1).equals(file.getRecycleRoot())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "请操作对应的私密回收站根节点");
        }
        return file;
    }

    /**
     * 校验私密回收站项未过期。
     */
    private void validateTrashUnexpired(SysFile file) {
        LocalDateTime expireAt = file.getExpireAt();
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "私密回收站文件已过期");
        }
    }

    /**
     * 解析私密回收站恢复位置。
     */
    private RestoreTarget resolveRestoreTarget(Long userId, SysFile root) {
        Long parentId = root.getParentId() == null ? ROOT_ID : root.getParentId();
        if (ROOT_ID == parentId) {
            return new RestoreTarget(ROOT_ID, "");
        }
        SysFile parent = sysFileMapper.selectSpaceFile(SPACE_TYPE_PRIVATE, userId, parentId);
        if (parent == null || !isDirectory(parent) || isInTrash(parent)) {
            return new RestoreTarget(ROOT_ID, "");
        }
        return new RestoreTarget(parent.getId(), parent.getFullPath());
    }

    /**
     * 解析私密回收站恢复名称。
     */
    private String resolveRestoreName(Long userId, SysFile root, Long parentId, ConflictPolicy conflictPolicy) {
        SysFile conflict = sysFileMapper.selectActiveByNameInSpaceDirectory(
                SPACE_TYPE_PRIVATE, userId, parentId, root.getOriginalName());
        if (conflict == null) {
            return root.getOriginalName();
        }
        if (ConflictPolicy.RENAME.equals(conflictPolicy)) {
            return generateUniquePrivateName(userId, parentId, root.getOriginalName());
        }
        if (!ConflictPolicy.OVERWRITE.equals(conflictPolicy)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录下已存在同名文件或目录");
        }
        moveActiveRootToTrash(userId, conflict);
        return root.getOriginalName();
    }

    /**
     * 释放永久删除文件的物理对象引用。
     */
    private void decreaseReferencesForDeletedTree(SysFile root, List<SysFile> descendants) {
        decreaseReferenceIfFile(root);
        descendants.forEach(this::decreaseReferenceIfFile);
    }

    /**
     * 文件节点减少物理对象引用。
     */
    private void decreaseReferenceIfFile(SysFile file) {
        if (Integer.valueOf(0).equals(file.getIsDirectory())) {
            fileObjectService.decreaseReferenceOrRemove(file);
        }
    }

    /** 查询同一私密回收站根节点下的后代。 */
    private List<SysFile> trashTreeDescendants(Long userId, Long rootId) {
        return sysFileMapper.selectDescendantsInSpace(SPACE_TYPE_PRIVATE, userId, rootId);
    }

    /**
     * 获取上传文件名。
     */
    private String resolveUploadName(MultipartFile file) {
        if (file == null || StrUtil.isBlank(file.getOriginalFilename())) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "上传文件不能为空");
        }
        return FileUtil.getName(file.getOriginalFilename());
    }

    /**
     * 生成私密空间不重名文件名。
     */
    private String generateUniquePrivateName(Long userId, Long parentId, String originalName) {
        if (!sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PRIVATE, userId, parentId, originalName)) {
            return originalName;
        }
        String mainName = FileUtil.mainName(originalName);
        String extName = FileUtil.extName(originalName);
        Set<String> existingNames = new HashSet<>(sysFileMapper.selectNamesInSpaceDirectory(
                SPACE_TYPE_PRIVATE, userId, parentId, mainName));
        for (int index = 1; ; index++) {
            String candidate = formatDuplicateName(mainName, extName, index);
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * 格式化重名序号。
     */
    private String formatDuplicateName(String mainName, String extName, int index) {
        String suffix = "(" + index + ")";
        return StrUtil.isBlank(extName) ? mainName + suffix : mainName + suffix + "." + extName;
    }

    /**
     * 回滚上传过程中已预留的容量和对象引用。
     */
    private void rollbackUploadReservation(Long userId, long fileSize, boolean quotaReserved,
                                           StoredFileObjectBO storedObject, SysFile privateFile) {
        if (privateFile != null) {
            fileObjectService.decreaseReferenceOrRemove(privateFile);
        } else if (storedObject != null) {
            fileObjectService.decreaseReferenceOrRemove(storedObject);
        }
        if (quotaReserved) {
            quotaService.decreaseUsedSpace(userId, fileSize);
        }
    }

    /**
     * 是否为目录。
     */
    private boolean isDirectory(SysFile file) {
        return Integer.valueOf(1).equals(file.getIsDirectory());
    }

    /**
     * 是否已在回收站。
     */
    private boolean isInTrash(SysFile file) {
        return Integer.valueOf(1).equals(file.getInRecycleBin());
    }

    private boolean isDescendantPath(Long directoryId, String targetFullPath) {
        return StringUtils.parseIdList(targetFullPath).contains(directoryId);
    }

    private long calculateLogicalSize(SysFile root, List<SysFile> descendants) {
        long total = fileSizeOf(root);
        for (SysFile child : descendants) {
            total += fileSizeOf(child);
        }
        return total;
    }

    private long fileSizeOf(SysFile file) {
        return isDirectory(file) ? 0L : file.getFileSize() == null ? 0L : file.getFileSize();
    }

    private record RestoreTarget(Long parentId, String parentFullPath) {
    }

    /**
     * 构造降级提醒文案。
     */
    private String buildReminder(PrivateSpaceState state, PrivateSpace privateSpace) {
        if (state == PrivateSpaceState.GRACE_PERIOD) {
            return "VIP 已降级，私密空间处于宽限期";
        }
        if (state == PrivateSpaceState.EXPIRED && privateSpace != null) {
            return "VIP 宽限期已结束，私密空间已过期";
        }
        return null;
    }

    /**
     * 校验当前用户是 VIP。
     */
    private void requireVip(Long userId) {
        if (!quotaService.isVip(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "私密空间仅 VIP 用户可用");
        }
    }

    /**
     * 查询已设置密码的私密空间。
     */
    private PrivateSpace requireEnabledPrivateSpace(Long userId) {
        PrivateSpace privateSpace = privateSpaceMapper.selectByUserId(userId);
        if (privateSpace == null || StrUtil.isBlank(privateSpace.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "请先设置私密空间密码");
        }
        return privateSpace;
    }

    /**
     * 修改密码时校验旧密码。
     */
    private void requireOldPassword(PrivateSpace privateSpace, String oldPassword) {
        if (StrUtil.isBlank(privateSpace.getPasswordHash())) {
            return;
        }
        if (StrUtil.isBlank(oldPassword)) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "旧密码不能为空");
        }
        if (!passwordEncoder.matches(oldPassword, privateSpace.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR, "旧密码错误");
        }
    }

    /**
     * 获取当前 token 对应的解锁截止时间。
     */
    private LocalDateTime getUnlockedUntil(Long userId) {
        Long ttlSeconds = stringRedisTemplate.getExpire(sessionKey(userId), TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(ttlSeconds);
    }

    /**
     * 保存当前登录 token 对应的解锁会话。
     */
    private LocalDateTime saveUnlockSession(Long userId) {
        LocalDateTime unlockedUntil = LocalDateTime.now().plus(UNLOCK_TTL);
        stringRedisTemplate.opsForValue().set(sessionKey(userId), SESSION_VALUE, UNLOCK_TTL);
        return unlockedUntil;
    }

    /**
     * 删除当前登录 token 对应的解锁会话。
     */
    private void deleteUnlockSession(Long userId) {
        stringRedisTemplate.delete(sessionKey(userId));
    }

    /**
     * 当前登录 token 绑定的 Redis key。
     */
    private String sessionKey(Long userId) {
        return SESSION_KEY_FORMAT.formatted(userId, JwtTokenUtils.getCurrentTokenHash());
    }
}
