package com.jiayuan.boot.system.oss.service.impl;

import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.oss.config.TrashRetentionProperties;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站到期与私密空间宽限期清理服务。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashRetentionCleanupService {

    private static final String SPACE_TYPE_PRIVATE = "PRIVATE";
    private static final long VIP_TOTAL_QUOTA = Long.MAX_VALUE;

    private final SysFileMapper sysFileMapper;
    private final FileObjectService fileObjectService;
    private final QuotaService quotaService;
    private final TrashRetentionProperties properties;
    private final SystemConfigProperties configProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行一次回收站到期清理。
     *
     * @return 清理结果
     */
    public CleanupResult cleanupExpiredRetention() {
        LocalDateTime now = LocalDateTime.now();
        int movedPrivateRoots = moveExpiredPrivateGraceRoots(now);
        int deletedTrashRoots = deleteExpiredTrashRoots(now);
        CleanupResult result = new CleanupResult(deletedTrashRoots, movedPrivateRoots);
        log.info("trash_retention_cleanup_completed deletedTrashRoots={} movedPrivateRoots={}",
                result.deletedTrashRoots(), result.movedPrivateRoots());
        return result;
    }

    private int deleteExpiredTrashRoots(LocalDateTime now) {
        List<SysFile> roots = sysFileMapper.selectExpiredTrashRoots(now, properties.cleanupBatchSize());
        int deletedCount = 0;
        for (SysFile root : roots) {
            if (executeCleanup("delete_expired_trash", root, () -> deleteExpiredTrashRoot(root))) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private boolean deleteExpiredTrashRoot(SysFile root) {
        List<SysFile> descendants = isDirectory(root)
                ? sysFileMapper.selectDescendantsInSpace(root.getSpaceType(), root.getSpaceId(), root.getId())
                : List.of();
        int deleted = sysFileMapper.permanentlyDeleteTrashTreeInSpace(
                root.getSpaceType(), root.getSpaceId(), root.getId());
        if (deleted == 0) {
            return false;
        }
        decreaseReferencesForDeletedTree(root, descendants);
        log.info("trash_retention_deleted_root spaceType={} spaceId={} rootId={} affectedRows={}",
                root.getSpaceType(), root.getSpaceId(), root.getId(), deleted);
        return true;
    }

    private int moveExpiredPrivateGraceRoots(LocalDateTime now) {
        List<SysFile> roots = sysFileMapper.selectExpiredPrivateGraceRoots(
                now, VIP_TOTAL_QUOTA, properties.cleanupBatchSize());
        int movedCount = 0;
        for (SysFile root : roots) {
            if (executeCleanup("move_private_grace_root", root, () -> movePrivateRootToTrash(root, now))) {
                movedCount++;
            }
        }
        return movedCount;
    }

    private boolean movePrivateRootToTrash(SysFile root, LocalDateTime deletedAt) {
        List<SysFile> descendants = isDirectory(root)
                ? sysFileMapper.selectActiveDescendantsInSpace(SPACE_TYPE_PRIVATE, root.getSpaceId(), root.getId())
                : List.of();
        long totalSize = calculateLogicalSize(root, descendants);
        LocalDateTime expireAt = deletedAt.plus(Duration.ofSeconds(configProperties.getTrashRetentionSeconds()));
        int moved = sysFileMapper.moveRootToTrashInSpaceWithExpireAt(
                SPACE_TYPE_PRIVATE, root.getSpaceId(), root.getId(), null, deletedAt, expireAt);
        if (moved == 0) {
            return false;
        }
        if (isDirectory(root)) {
            sysFileMapper.updateDescendantsRecycleStateInSpaceWithExpireAt(
                    SPACE_TYPE_PRIVATE, root.getSpaceId(), root.getId(), deletedAt, expireAt);
        }
        if (totalSize > 0) {
            quotaService.decreaseUsedSpace(root.getSpaceId(), totalSize);
        }
        log.info("private_grace_root_moved_to_trash userId={} rootId={} expireAt={} logicalSize={}",
                root.getSpaceId(), root.getId(), expireAt, totalSize);
        return true;
    }

    private boolean executeCleanup(String action, SysFile root, CleanupAction cleanupAction) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> cleanupAction.execute()));
        } catch (RuntimeException ex) {
            log.warn("trash_retention_cleanup_failed action={} spaceType={} spaceId={} rootId={}",
                    action, root.getSpaceType(), root.getSpaceId(), root.getId(), ex);
            return false;
        }
    }

    private void decreaseReferencesForDeletedTree(SysFile root, List<SysFile> descendants) {
        decreaseReferenceIfFile(root);
        descendants.forEach(this::decreaseReferenceIfFile);
    }

    private void decreaseReferenceIfFile(SysFile file) {
        if (!isDirectory(file)) {
            fileObjectService.decreaseReferenceOrRemove(file);
        }
    }

    private boolean isDirectory(SysFile file) {
        return Integer.valueOf(1).equals(file.getIsDirectory());
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

    /**
     * 单次清理结果。
     *
     * @param deletedTrashRoots 已彻底删除的过期回收站根节点数
     * @param movedPrivateRoots 已移入私密回收站的私密空间根节点数
     */
    public record CleanupResult(int deletedTrashRoots, int movedPrivateRoots) {
    }

    private interface CleanupAction {

        boolean execute();
    }
}
