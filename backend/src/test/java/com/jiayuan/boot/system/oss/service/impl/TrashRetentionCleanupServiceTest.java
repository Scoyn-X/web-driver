package com.jiayuan.boot.system.oss.service.impl;

import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.oss.config.TrashRetentionProperties;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收站到期清理服务单元测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrashRetentionCleanupService 单元测试")
class TrashRetentionCleanupServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long TEAM_ID = 9L;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private FileObjectService fileObjectService;
    @Mock private QuotaService quotaService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    @Test
    @DisplayName("清理过期回收站：递归删除并释放文件对象引用")
    void cleanupExpiredRetention_deletesExpiredTrashRootsAndReleasesReferences() {
        TrashRetentionCleanupService service = cleanupService();
        SysFile root = directory("TEAM", TEAM_ID, 10L, 0L);
        SysFile childFile = file("TEAM", TEAM_ID, 11L, 8L, "hash-child");
        childFile.setParentId(10L);
        SysFile childDirectory = directory("TEAM", TEAM_ID, 12L, 10L);
        SysFile grandchild = file("TEAM", TEAM_ID, 13L, 5L, "hash-grandchild");
        grandchild.setParentId(12L);
        when(sysFileMapper.selectExpiredPrivateGraceRoots(any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(sysFileMapper.selectExpiredTrashRoots(any(), anyInt())).thenReturn(List.of(root));
        when(sysFileMapper.selectDescendantsInSpace("TEAM", TEAM_ID, 10L))
                .thenReturn(List.of(childFile, childDirectory, grandchild));
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L)).thenReturn(3);

        TrashRetentionCleanupService.CleanupResult result = service.cleanupExpiredRetention();

        assertThat(result.deletedTrashRoots()).isEqualTo(1);
        assertThat(result.movedPrivateRoots()).isZero();
        verify(fileObjectService).decreaseReferenceOrRemove(childFile);
        verify(fileObjectService).decreaseReferenceOrRemove(grandchild);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(root);
    }

    @Test
    @DisplayName("私密空间宽限期到期：活动根节点移入私密回收站并释放容量")
    void cleanupExpiredRetention_movesExpiredPrivateGraceRootsToTrash() {
        TrashRetentionCleanupService service = cleanupService();
        SysFile root = directory("PRIVATE", USER_ID, 20L, 0L);
        root.setInRecycleBin(0);
        root.setRecycleRoot(0);
        SysFile childFile = file("PRIVATE", USER_ID, 21L, 8L, "hash-private");
        childFile.setParentId(20L);
        childFile.setInRecycleBin(0);
        childFile.setRecycleRoot(0);
        when(sysFileMapper.selectExpiredPrivateGraceRoots(any(), anyLong(), anyInt()))
                .thenReturn(List.of(root));
        when(sysFileMapper.selectActiveDescendantsInSpace("PRIVATE", USER_ID, 20L))
                .thenReturn(List.of(childFile));
        when(sysFileMapper.moveRootToTrashInSpaceWithExpireAt(
                eq("PRIVATE"), eq(USER_ID), eq(20L), isNull(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(sysFileMapper.selectExpiredTrashRoots(any(), anyInt())).thenReturn(List.of());

        TrashRetentionCleanupService.CleanupResult result = service.cleanupExpiredRetention();

        assertThat(result.movedPrivateRoots()).isEqualTo(1);
        verify(sysFileMapper).updateDescendantsRecycleStateInSpaceWithExpireAt(
                any(), any(), any(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(quotaService).decreaseUsedSpace(USER_ID, 8L);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    private TrashRetentionCleanupService cleanupService() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        return new TrashRetentionCleanupService(
                sysFileMapper,
                fileObjectService,
                quotaService,
                new TrashRetentionProperties(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(30), 100),
                new SystemConfigProperties(), transactionTemplate);
    }

    private SysFile directory(String spaceType, Long spaceId, Long id, Long parentId) {
        SysFile file = base(spaceType, spaceId, id, parentId);
        file.setIsDirectory(1);
        return file;
    }

    private SysFile file(String spaceType, Long spaceId, Long id, Long size, String hash) {
        SysFile file = base(spaceType, spaceId, id, 0L);
        file.setIsDirectory(0);
        file.setFileSize(size);
        file.setFileHash(hash);
        file.setFilePath("202605/" + hash + ".bin");
        return file;
    }

    private SysFile base(String spaceType, Long spaceId, Long id, Long parentId) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setSpaceType(spaceType);
        file.setSpaceId(spaceId);
        file.setUserId(spaceId);
        file.setParentId(parentId);
        file.setOriginalName("file-" + id);
        file.setInRecycleBin(1);
        file.setRecycleRoot(1);
        return file;
    }
}
