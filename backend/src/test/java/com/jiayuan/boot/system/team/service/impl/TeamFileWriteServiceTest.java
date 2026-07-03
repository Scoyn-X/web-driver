package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.model.bo.FileCloneBuildBO;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.bo.TeamDirectoryBuildBO;
import com.jiayuan.boot.system.oss.model.bo.TeamFileBuildBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团队文件变更支持组件单元测试。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamFileWriteService 单元测试")
class TeamFileWriteServiceTest extends TeamFileWriteServiceTestSupport {

    @Test
    @DisplayName("创建团队目录：锁定目标目录并写入 fullPath")
    void createDirectory_success() {
        DirectoryCreateRequestVO request = new DirectoryCreateRequestVO();
        request.setName("设计稿");
        request.setParentId(2L);
        SysFile directory = teamDirectory(null, 2L, null);
        directory.setOriginalName("设计稿");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 2L)).thenReturn(teamDirectory(2L, 0L, "2"));
        when(sysFileConverter.toTeamDirectory(any(TeamDirectoryBuildBO.class))).thenReturn(directory);
        assignInsertedIds(10L);

        SysFile result = withCurrentUser(() -> writeService.createDirectory(TEAM_ID, request));

        assertThat(result.getFullPath()).isEqualTo("2,10");
        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 2L);
        verify(sysFileMapper).insert(directory);
        verify(sysFileMapper).updateById(directory);
    }

    @Test
    @DisplayName("上传团队文件：预留配额，保存对象并写入元数据")
    void uploadFile_checksQuotaSavesObjectAndIncreasesUsedSpace() {
        MultipartFile multipartFile = uploadFile();
        StoredFileObjectBO storedObject = storedObject(3L, "hash-a");
        SysFile savedFile = teamFile(null, 0);
        savedFile.setFileHash("hash-a");
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toTeamUploadedFile(any(TeamFileBuildBO.class))).thenReturn(savedFile);
        assignInsertedIds(20L);

        SysFile result = withCurrentUser(() -> writeService.uploadFile(TEAM_ID, multipartFile, null));

        assertThat(result.getFullPath()).isEqualTo("20");
        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(teamQuotaService).checkQuota(TEAM_ID, 3L);
        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 0L);
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 3L);
        verify(fileObjectService).saveOrReuse(multipartFile);
    }

    @Test
    @DisplayName("上传团队文件：保存对象失败时释放已预留容量")
    void uploadFile_releasesReservedQuotaWhenObjectSaveFails() {
        MultipartFile multipartFile = uploadFile();
        BusinessException failure = new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, "对象保存失败");
        when(fileObjectService.saveOrReuse(multipartFile)).thenThrow(failure);

        assertThatThrownBy(() -> withCurrentUser(() -> writeService.uploadFile(TEAM_ID, multipartFile, null)))
                .isSameAs(failure);

        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 3L);
        verify(teamQuotaService).decreaseUsedSpace(TEAM_ID, 3L);
    }

    @Test
    @DisplayName("上传团队文件：元数据写入失败时释放对象引用和容量")
    void uploadFile_releasesObjectReferenceWhenMetadataInsertFails() {
        MultipartFile multipartFile = uploadFile();
        SysFile savedFile = teamFile(null, 0);
        BusinessException failure = new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, "写入失败");
        StoredFileObjectBO storedObject = storedObject(3L, "hash-a");
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toTeamUploadedFile(any(TeamFileBuildBO.class))).thenReturn(savedFile);
        when(sysFileMapper.insert(savedFile)).thenThrow(failure);

        assertThatThrownBy(() -> withCurrentUser(() -> writeService.uploadFile(TEAM_ID, multipartFile, null)))
                .isSameAs(failure);

        verify(fileObjectService).decreaseReferenceOrRemove(storedObject);
        verify(teamQuotaService).decreaseUsedSpace(TEAM_ID, 3L);
    }

    @Test
    @DisplayName("移动团队目录：更新父级路径并刷新后代路径")
    void moveFile_directoryUpdatesParentFullPathAndDescendants() {
        SysFile source = teamDirectory(10L, 0L, "10");
        source.setOriginalName("docs");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 2L)).thenReturn(teamDirectory(2L, 0L, "2"));

        writeService.moveFile(TEAM_ID, 10L, 2L);

        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 2L);
        verify(sysFileMapper).updateRootLocationInSpace("TEAM", TEAM_ID, 10L, 2L, "2,10");
        verify(sysFileMapper).updateDescendantsFullPathInSpace("TEAM", TEAM_ID, 10L, "2,10");
    }

    @Test
    @DisplayName("复制团队目录：仅复制活动后代并增加文件引用")
    void copyFile_directoryIncreasesReferencesAndQuota() {
        SysFile source = teamDirectory(10L, 0L, "10");
        source.setOriginalName("docs");
        SysFile childFile = teamFile(11L, 0);
        childFile.setParentId(10L);
        childFile.setOriginalName("child.txt");
        childFile.setFullPath("10,11");
        childFile.setFileSize(5L);
        childFile.setFileHash("hash-child");
        SysFile[] copies = new SysFile[2];
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 2L)).thenReturn(teamDirectory(2L, 0L, "2"));
        when(sysFileMapper.existsTeamNameInDirectory(TEAM_ID, 2L, "docs")).thenReturn(true);
        when(sysFileMapper.selectTeamNamesInDirectory(TEAM_ID, 2L, "docs")).thenReturn(List.of("docs", "docs(1)"));
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 10L)).thenReturn(List.of(childFile));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            SysFile copy = teamFile(null, build.getSource().getIsDirectory());
            copy.setOriginalName(build.getOriginalName());
            copy.setParentId(build.getParentId());
            copy.setFileSize(build.getSource().getFileSize());
            copy.setFileHash(build.getSource().getFileHash());
            copies[copies[0] == null ? 0 : 1] = copy;
            return copy;
        });
        assignInsertedIds(30L, 31L);

        withCurrentUser(() -> writeService.copyFile(TEAM_ID, 10L, 2L));

        assertThat(copies[0].getFullPath()).isEqualTo("2,30");
        assertThat(copies[1].getFullPath()).isEqualTo("2,30,31");
        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper, never()).selectDescendantsInSpace("TEAM", TEAM_ID, 10L);
        verify(fileObjectService).increaseReference("hash-child");
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 5L);
    }

    @Test
    @DisplayName("转存个人目录到团队：默认根目录、自动重命名并复用对象引用")
    void transferFromPersonal_directoryDefaultsRootRenamesAndIncreasesReferences() {
        SysFile source = personalDirectory(50L, 0L, "50");
        source.setOriginalName("docs");
        SysFile childFile = personalFile(51L, "child.txt", 5L, "hash-child");
        childFile.setParentId(50L);
        childFile.setFullPath("50,51");
        SysFile nestedDirectory = personalDirectory(52L, 50L, "50,52");
        nestedDirectory.setOriginalName("nested");
        SysFile nestedFile = personalFile(53L, "nested.txt", 7L, "hash-nested");
        nestedFile.setParentId(52L);
        nestedFile.setFullPath("50,52,53");
        SysFile[] copies = new SysFile[4];
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectAllDescendants(50L))
                .thenReturn(List.of(nestedFile, childFile, nestedDirectory));
        when(sysFileMapper.existsTeamNameInDirectory(TEAM_ID, 0L, "docs")).thenReturn(true);
        when(sysFileMapper.selectTeamNamesInDirectory(TEAM_ID, 0L, "docs")).thenReturn(List.of("docs", "docs(1)"));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            SysFile copy = teamFile(null, build.getSource().getIsDirectory());
            copy.setOriginalName(build.getOriginalName());
            copy.setParentId(build.getParentId());
            copy.setFileSize(build.getSource().getFileSize());
            copy.setFileHash(build.getSource().getFileHash());
            copies[copies[0] == null ? 0 : copies[1] == null ? 1 : copies[2] == null ? 2 : 3] = copy;
            return copy;
        });
        assignInsertedIds(60L, 61L, 62L, 63L);

        withCurrentUser(() -> writeService.transferFromPersonal(TEAM_ID, 50L, null, null));

        assertThat(copies[0].getOriginalName()).isEqualTo("docs(2)");
        assertThat(copies[0].getFullPath()).isEqualTo("60");
        assertThat(copies[1].getOriginalName()).isEqualTo("child.txt");
        assertThat(copies[1].getFullPath()).isEqualTo("60,61");
        assertThat(copies[2].getOriginalName()).isEqualTo("nested");
        assertThat(copies[2].getFullPath()).isEqualTo("60,62");
        assertThat(copies[3].getOriginalName()).isEqualTo("nested.txt");
        assertThat(copies[3].getFullPath()).isEqualTo("60,62,63");
        verify(teamQuotaService).checkQuota(TEAM_ID, 12L);
        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 0L);
        verify(fileObjectService).increaseReference("hash-child");
        verify(fileObjectService).increaseReference("hash-nested");
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 12L);
    }

    @Test
    @DisplayName("转存个人文件到团队：OVERWRITE 仍按自动重命名转存")
    void transferFromPersonal_compatConflictPolicyIgnoredAndUsesAutoRename() {
        SysFile source = personalFile(50L, "report.pdf", 5L, "hash-report");
        SysFile[] copied = new SysFile[1];
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.existsTeamNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(true);
        when(sysFileMapper.selectTeamNamesInDirectory(TEAM_ID, 0L, "report"))
                .thenReturn(List.of("report.pdf", "report(1).pdf"));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            copied[0] = teamFile(null, 0);
            copied[0].setOriginalName(build.getOriginalName());
            copied[0].setFileSize(build.getSource().getFileSize());
            copied[0].setFileHash(build.getSource().getFileHash());
            return copied[0];
        });
        assignInsertedIds(60L);

        withCurrentUser(() -> writeService.transferFromPersonal(TEAM_ID, 50L, 0L, ConflictPolicy.OVERWRITE));

        assertThat(copied[0].getOriginalName()).isEqualTo("report(2).pdf");
        verify(fileObjectService).increaseReference("hash-report");
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 5L);
        verify(sysFileMapper, never()).selectAllDescendants(50L);
    }

    @Test
    @DisplayName("转存个人文件到团队：源文件缺少 fileHash 时在配额前拒绝")
    void transferFromPersonal_blankSourceFileHashRejectedBeforeQuotaAndReferences() {
        SysFile source = personalFile(50L, "legacy.pdf", 5L, " ");
        assertPersonalSourceTransferRejected(source, ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人文件到团队：源文件 isDirectory 非 0/1 时在配额前拒绝")
    void transferFromPersonal_malformedSourceIsDirectoryRejectedBeforeQuotaAndReferences() {
        SysFile source = personalFile(50L, "legacy.pdf", 5L, "hash-legacy");
        source.setIsDirectory(2);
        assertPersonalSourceTransferRejected(source, ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人文件到团队：源文件 isDirectory 为空时在配额前拒绝")
    void transferFromPersonal_nullSourceIsDirectoryRejectedBeforeQuotaAndReferences() {
        SysFile source = personalFile(50L, "legacy.pdf", 5L, "hash-legacy");
        source.setIsDirectory(null);
        assertPersonalSourceTransferRejected(source, ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人文件到团队：源文件在回收站时拒绝转存")
    void transferFromPersonal_recycledSourceRejectedBeforeTransferWork() {
        SysFile source = personalFile(50L, "report.pdf", 5L, "hash-report");
        source.setInRecycleBin(1);
        assertPersonalSourceTransferRejected(source, ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("转存个人文件到团队：非当前用户个人文件不可转存")
    void transferFromPersonal_nonOwnedSourceRejected() {
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(null);

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(sysFileMapper).selectPersonalFile(USER_ID, 50L);
        verify(sysFileMapper, never()).selectSpaceFile(any(), any(), any());
        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
    }

    @Test
    @DisplayName("转存个人目录到团队：后代不属于当前用户时拒绝转存")
    void transferFromPersonal_nonOwnedDescendantRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "leaked.txt", 5L, "hash-leaked");
        descendant.setUserId(99L);
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 spaceId 不属于当前用户时拒绝转存")
    void transferFromPersonal_wrongDescendantSpaceIdRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "leaked.txt", 5L, "hash-leaked");
        descendant.setSpaceId(99L);
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 spaceType 不是 PERSONAL 时拒绝转存")
    void transferFromPersonal_wrongDescendantSpaceTypeRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "leaked.txt", 5L, "hash-leaked");
        descendant.setSpaceType("TEAM");
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("转存个人目录到团队：跳过回收站后代且不占用团队容量")
    void transferFromPersonal_recycledDescendantSkippedWithoutQuotaOrReferenceIncrease() {
        SysFile source = personalDirectory(50L, 0L, "50");
        source.setOriginalName("docs");
        SysFile activeChild = personalFile(51L, "child.txt", 5L, "hash-child");
        activeChild.setParentId(50L);
        activeChild.setFullPath("50,51");
        SysFile recycledChild = personalFile(52L, "deleted.txt", 7L, "hash-deleted");
        recycledChild.setInRecycleBin(1);
        recycledChild.setParentId(50L);
        recycledChild.setFullPath("50,52");
        SysFile[] copies = new SysFile[2];
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectAllDescendants(50L)).thenReturn(List.of(activeChild, recycledChild));
        when(sysFileMapper.selectPersonalActiveTree(USER_ID)).thenReturn(List.of(source, activeChild));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            SysFile copy = teamFile(null, build.getSource().getIsDirectory());
            copy.setOriginalName(build.getOriginalName());
            copy.setParentId(build.getParentId());
            copy.setFileSize(build.getSource().getFileSize());
            copy.setFileHash(build.getSource().getFileHash());
            copies[copies[0] == null ? 0 : 1] = copy;
            return copy;
        });
        assignInsertedIds(60L, 61L);

        withCurrentUser(() -> writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null));

        assertThat(copies[0].getOriginalName()).isEqualTo("docs");
        assertThat(copies[0].getFullPath()).isEqualTo("60");
        assertThat(copies[1].getOriginalName()).isEqualTo("child.txt");
        assertThat(copies[1].getFullPath()).isEqualTo("60,61");
        verify(teamQuotaService).checkQuota(TEAM_ID, 5L);
        verify(fileObjectService).increaseReference("hash-child");
        verify(fileObjectService, never()).increaseReference("hash-deleted");
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 5L);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 fullPath 非数字时在配额前拒绝")
    void transferFromPersonal_malformedDescendantFullPathRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "bad.txt", 5L, "hash-bad");
        descendant.setParentId(50L);
        descendant.setFullPath("50,abc,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 fullPath 缺少源根节点时在配额前拒绝")
    void transferFromPersonal_descendantPathWithoutSourceRootRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "bad.txt", 5L, "hash-bad");
        descendant.setParentId(50L);
        descendant.setFullPath("99,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代父节点缺失时在配额前拒绝")
    void transferFromPersonal_missingParentMappingRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "orphan.txt", 5L, "hash-orphan");
        descendant.setParentId(52L);
        descendant.setFullPath("50,52,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代文件缺少 fileHash 时在配额前拒绝")
    void transferFromPersonal_blankDescendantFileHashRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "legacy.txt", 5L, null);
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 isDirectory 非 0/1 时在配额前拒绝")
    void transferFromPersonal_malformedDescendantIsDirectoryRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "legacy.txt", 5L, "hash-legacy");
        descendant.setIsDirectory(null);
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：后代 isDirectory 为 2 时在配额前拒绝")
    void transferFromPersonal_numericMalformedDescendantIsDirectoryRejectedBeforeQuotaAndReferences() {
        SysFile descendant = personalFile(51L, "legacy.txt", 5L, "hash-legacy");
        descendant.setIsDirectory(2);
        descendant.setParentId(50L);
        descendant.setFullPath("50,51");
        assertPersonalDirectoryTransferRejected(List.of(descendant), ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：源根 fullPath 父级不匹配时在配额前拒绝")
    void transferFromPersonal_rootFullPathParentMismatchRejectedBeforeQuotaAndReferences() {
        SysFile source = personalDirectory(50L, 0L, "49,50");
        source.setOriginalName("docs");
        assertPersonalSourceTransferRejected(source, ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("转存个人目录到团队：fullPath 位于源下但 parent 链断开的活动行在配额前拒绝")
    void transferFromPersonal_fullPathOnlyOrphanRejectedBeforeQuotaAndReferences() {
        SysFile source = personalDirectory(50L, 0L, "50");
        source.setOriginalName("docs");
        SysFile linkedChild = personalFile(51L, "child.txt", 5L, "hash-child");
        linkedChild.setParentId(50L);
        linkedChild.setFullPath("50,51");
        SysFile fullPathOnlyOrphan = personalFile(52L, "orphan.txt", 7L, "hash-orphan");
        fullPathOnlyOrphan.setParentId(999L);
        fullPathOnlyOrphan.setFullPath("50,52");
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectAllDescendants(50L)).thenReturn(List.of(linkedChild));
        when(sysFileMapper.selectPersonalActiveTree(USER_ID))
                .thenReturn(List.of(source, linkedChild, fullPathOnlyOrphan));

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("转存个人文件到团队：存在活动子节点时在配额前拒绝")
    void transferFromPersonal_fileSourceWithActiveChildParentRejectedBeforeQuotaAndReferences() {
        SysFile source = personalFile(50L, "report.pdf", 5L, "hash-report");
        SysFile stalePathChild = personalFile(51L, "stale.txt", 7L, "hash-stale");
        stalePathChild.setParentId(50L);
        stalePathChild.setFullPath("999,51");
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectPersonalActiveTree(USER_ID)).thenReturn(List.of(source, stalePathChild));

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).selectAllDescendants(anyLong());
        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("转存个人目录到团队：源路径下活动行 fullPath 非数字时在配额前拒绝")
    void transferFromPersonal_malformedFullPathOnlyActiveTreeOrphanRejectedBeforeQuotaAndReferences() {
        SysFile source = personalDirectory(50L, 0L, "50");
        source.setOriginalName("docs");
        SysFile malformedFullPathOnlyOrphan = personalFile(52L, "orphan.txt", 7L, "hash-orphan");
        malformedFullPathOnlyOrphan.setParentId(999L);
        malformedFullPathOnlyOrphan.setFullPath("50,abc,52");
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectAllDescendants(50L)).thenReturn(List.of());
        when(sysFileMapper.selectPersonalActiveTree(USER_ID))
                .thenReturn(List.of(source, malformedFullPathOnlyOrphan));

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    void transferToPersonal_fileDefaultsRootRenamesChecksQuotaAndReturnsPersonalFile() {
        SysFile source = teamFile(70L, 0);
        source.setOriginalName("report.pdf"); source.setFileSize(5L); source.setFileHash("hash-report"); source.setFullPath("70");
        SysFile[] copied = new SysFile[1];
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        when(sysFileMapper.existsNameInSpaceDirectory("PERSONAL", USER_ID, 0L, "report.pdf")).thenReturn(true);
        when(sysFileMapper.selectNamesInSpaceDirectory("PERSONAL", USER_ID, 0L, "report"))
                .thenReturn(List.of("report.pdf", "report(1).pdf"));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            copied[0] = personalFile(null, build.getOriginalName(), build.getSource().getFileSize(), build.getSource().getFileHash());
            copied[0].setParentId(build.getParentId());
            return copied[0];
        });
        assignInsertedIds(80L);
        SysFile result = withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, null, null));
        assertThat(result).isSameAs(copied[0]);
        assertThat(copied[0].getOriginalName()).isEqualTo("report(2).pdf");
        assertThat(copied[0].getFullPath()).isEqualTo("80");
        verify(quotaService).checkSingleFileLimit(USER_ID, 5L);
        verify(quotaService).checkQuota(USER_ID, 5L);
        verify(fileObjectService).increaseReference("hash-report");
        verify(quotaService).increaseUsedSpace(USER_ID, 5L);
        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
    }

    @Test
    void transferToPersonal_directoryCopiesActiveDescendantsPreservingStructure() {
        SysFile source = teamDirectory(70L, 0L, "70");
        source.setOriginalName("docs");
        SysFile childFile = teamFile(71L, 0);
        childFile.setOriginalName("child.txt"); childFile.setParentId(70L); childFile.setFullPath("70,71");
        childFile.setFileSize(5L); childFile.setFileHash("hash-child");
        SysFile nestedDirectory = teamDirectory(72L, 70L, "70,72");
        nestedDirectory.setOriginalName("nested");
        SysFile nestedFile = teamFile(73L, 0);
        nestedFile.setOriginalName("nested.txt"); nestedFile.setParentId(72L); nestedFile.setFullPath("70,72,73");
        nestedFile.setFileSize(7L); nestedFile.setFileHash("hash-nested");
        SysFile[] copies = new SysFile[4];
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 70L))
                .thenReturn(List.of(nestedFile, childFile, nestedDirectory));
        when(sysFileConverter.toClonedFile(any(FileCloneBuildBO.class))).thenAnswer(invocation -> {
            FileCloneBuildBO build = invocation.getArgument(0);
            SysFile copy = personalFile(null, build.getOriginalName(), build.getSource().getFileSize(), build.getSource().getFileHash());
            copy.setIsDirectory(build.getSource().getIsDirectory());
            copy.setParentId(build.getParentId());
            copies[copies[0] == null ? 0 : copies[1] == null ? 1 : copies[2] == null ? 2 : 3] = copy;
            return copy;
        });
        assignInsertedIds(80L, 81L, 82L, 83L);
        withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, 0L, ConflictPolicy.OVERWRITE));
        assertThat(copies).extracting(SysFile::getFullPath).containsExactly("80", "80,81", "80,82", "80,82,83");
        verify(quotaService).checkSingleFileLimit(USER_ID, 5L);
        verify(quotaService).checkSingleFileLimit(USER_ID, 7L);
        verify(quotaService).checkQuota(USER_ID, 12L);
        verify(fileObjectService).increaseReference("hash-child");
        verify(fileObjectService).increaseReference("hash-nested");
        verify(quotaService).increaseUsedSpace(USER_ID, 12L);
    }

    @Test
    void transferToPersonal_blankFileHashRejectedBeforeSideEffects() {
        SysFile source = teamFile(70L, 0); source.setFileHash(" "); source.setFullPath("70");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        assertThatThrownBy(() -> withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, 0L, null)))
                .isInstanceOf(BusinessException.class).extracting("resultCode").isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        verifyNoPersonalTransferSideEffects();
    }
    @Test
    void transferToPersonal_recycledTargetPersonalDirectoryRejected() {
        SysFile source = teamFile(70L, 0); source.setFileHash("hash-report"); source.setFullPath("70");
        SysFile target = personalDirectory(90L, 0L, "90"); target.setInRecycleBin(1);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        when(sysFileMapper.selectPersonalFile(USER_ID, 90L)).thenReturn(target);
        assertThatThrownBy(() -> withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, 90L, null)))
                .isInstanceOf(BusinessException.class).extracting("resultCode").isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        verify(quotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }
    @Test
    void transferToPersonal_missingParentMappingRejectedBeforeSideEffects() {
        SysFile source = teamDirectory(70L, 0L, "70");
        SysFile orphan = teamFile(71L, 0); orphan.setParentId(72L); orphan.setFullPath("70,72,71"); orphan.setFileHash("hash-orphan");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 70L)).thenReturn(List.of(orphan));
        assertThatThrownBy(() -> withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, 0L, null)))
                .isInstanceOf(BusinessException.class).extracting("resultCode").isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        verifyNoPersonalTransferSideEffects();
    }

    @Test
    void transferToPersonal_fullActiveTreeOrphansRejectedBeforeSideEffects() {
        SysFile source = teamDirectory(70L, 0L, "70");
        SysFile linkedChild = teamFile(71L, 0); linkedChild.setParentId(70L); linkedChild.setFullPath("70,71"); linkedChild.setFileHash("hash-child");
        SysFile fullPathOnlyOrphan = teamFile(72L, 0); fullPathOnlyOrphan.setParentId(999L); fullPathOnlyOrphan.setFullPath("70,72"); fullPathOnlyOrphan.setFileHash("hash-orphan");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 70L)).thenReturn(source);
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 70L)).thenReturn(List.of(linkedChild));
        when(sysFileMapper.selectTeamActiveTree(TEAM_ID)).thenReturn(List.of(source, linkedChild, fullPathOnlyOrphan));
        assertThatThrownBy(() -> withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 70L, 0L, null)))
                .isInstanceOf(BusinessException.class).extracting("resultCode").isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        SysFile fileSource = teamFile(73L, 0); fileSource.setOriginalName("report.pdf"); fileSource.setFileSize(5L); fileSource.setFileHash("hash-report"); fileSource.setFullPath("73");
        SysFile activeChild = teamFile(74L, 0); activeChild.setParentId(73L); activeChild.setFullPath("999,74"); activeChild.setFileHash("hash-child");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 73L)).thenReturn(fileSource);
        when(sysFileMapper.selectTeamActiveTree(TEAM_ID)).thenReturn(List.of(fileSource, activeChild));
        assertThatThrownBy(() -> withCurrentUser(() -> writeService.transferToPersonal(TEAM_ID, 73L, 0L, null)))
                .isInstanceOf(BusinessException.class).extracting("resultCode").isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        verifyNoPersonalTransferSideEffects();
    }

    private void assertPersonalDirectoryTransferRejected(List<SysFile> descendants, ResultCode expectedCode) {
        SysFile source = personalDirectory(50L, 0L, "50");
        source.setOriginalName("docs");
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);
        when(sysFileMapper.selectAllDescendants(50L)).thenReturn(descendants);

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(expectedCode);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    private void assertPersonalSourceTransferRejected(SysFile source, ResultCode expectedCode) {
        when(sysFileMapper.selectPersonalFile(USER_ID, 50L)).thenReturn(source);

        assertThatThrownBy(() -> withCurrentUser(() ->
                writeService.transferFromPersonal(TEAM_ID, 50L, 0L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(expectedCode);

        verify(sysFileMapper, never()).selectAllDescendants(anyLong());
        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    private void verifyNoPersonalTransferSideEffects() {
        verify(quotaService, never()).checkQuota(any(), anyLong());
        verify(quotaService, never()).checkSingleFileLimit(any(), anyLong());
        verify(fileObjectService, never()).increaseReference(any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("删除团队目录到回收站：按活动后代释放容量并重置所有后代回收根")
    void deleteToTrash_marksRecycleFlagsAndDecreasesQuota() {
        SysFile root = teamDirectory(10L, 0L, "10");
        SysFile childFile = teamFile(11L, 0);
        childFile.setFileSize(8L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 10L)).thenReturn(List.of(childFile));
        when(sysFileMapper.moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), eq(USER_ID), anyLong())).thenReturn(1);

        withCurrentUser(() -> {
            writeService.deleteToTrash(TEAM_ID, 10L);
            return null;
        });

        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper).moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), eq(USER_ID), anyLong());
        verify(sysFileMapper).updateDescendantsRecycleStateInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), anyLong());
        verify(sysFileMapper, never()).selectDescendantsInSpace("TEAM", TEAM_ID, 10L);
        verify(teamQuotaService).decreaseUsedSpace(TEAM_ID, 8L);
    }

    @Test
    @DisplayName("删除团队目录到回收站：无活动后代时仍重置嵌套回收根")
    void deleteToTrash_resetsNestedRecycleRootWithoutActiveDescendants() {
        SysFile root = teamDirectory(10L, 0L, "10");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectActiveDescendantsInSpace("TEAM", TEAM_ID, 10L)).thenReturn(List.of());
        when(sysFileMapper.moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), eq(USER_ID), anyLong())).thenReturn(1);

        withCurrentUser(() -> {
            writeService.deleteToTrash(TEAM_ID, 10L);
            return null;
        });

        verify(sysFileMapper).moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), eq(USER_ID), anyLong());
        verify(sysFileMapper).updateDescendantsRecycleStateInSpace(eq("TEAM"), eq(TEAM_ID), eq(10L), anyLong());
        verify(teamQuotaService, never()).decreaseUsedSpace(any(), anyLong());
    }

    @Test
    @DisplayName("恢复团队回收站目录：校验配额并递归恢复到原路径")
    void restoreFromTrash_directoryChecksQuotaAndRestoresRecursively() {
        SysFile root = teamDirectory(10L, 2L, "2,10");
        root.setOriginalName("docs");
        root.setInRecycleBin(1);
        root.setRecycleRoot(1);
        SysFile parent = teamDirectory(2L, 0L, "2");
        SysFile childFile = teamFile(11L, 0);
        childFile.setParentId(10L);
        childFile.setFileSize(8L);
        childFile.setInRecycleBin(1);
        childFile.setRecycleRoot(0);
        SysFile restored = teamDirectory(10L, 2L, "2,10");
        restored.setOriginalName("docs");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root, restored);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 2L)).thenReturn(parent);
        when(sysFileMapper.selectDescendantsInSpace("TEAM", TEAM_ID, 10L)).thenReturn(List.of(childFile));
        when(sysFileMapper.restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 2L, "2", "docs"))
                .thenReturn(1);

        SysFile result = writeService.restoreFromTrash(TEAM_ID, 10L, null);

        assertThat(result.getParentId()).isEqualTo(2L);
        assertThat(result.getFullPath()).isEqualTo("2,10");
        assertThat(result.getInRecycleBin()).isZero();
        assertThat(result.getRecycleRoot()).isZero();
        verify(teamQuotaService).checkQuota(TEAM_ID, 8L);
        verify(sysFileMapper).restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 2L, "2", "docs");
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 8L);
    }

    @Test
    @DisplayName("恢复团队回收站文件：同名冲突且未指定策略时报参数错误")
    void restoreFromTrash_conflictWithoutPolicyThrows() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        SysFile conflict = activeTeamFile(20L, "report.pdf", 0L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectTeamActiveByNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(conflict);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：过期根节点在恢复操作前被拒绝")
    void restoreFromTrash_expiredRootRejectedBeforeRestoreWork() {
        SysFile root = recycledTeamFile(10L, "expired.pdf", 8L);
        root.setExpireAt(LocalDateTime.now().minusDays(1));
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期")
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(sysFileMapper, never()).selectDescendantsInSpace(any(), any(), any());
        verify(sysFileMapper, never()).selectTeamActiveByNameInDirectory(any(), any(), any());
        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：同名冲突且未指定策略时携带冲突文件")
    void restoreFromTrash_conflictWithoutPolicyCarriesConflictFile() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        SysFile conflict = activeTeamFile(20L, "report.pdf", 0L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectTeamActiveByNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(conflict);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("conflictFile", conflict)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：RENAME 冲突策略生成不重名名称")
    void restoreFromTrash_renameConflictUsesUniqueName() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        SysFile conflict = activeTeamFile(20L, "report.pdf", 0L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectTeamActiveByNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(conflict);
        when(sysFileMapper.existsTeamNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(true);
        when(sysFileMapper.selectTeamNamesInDirectory(TEAM_ID, 0L, "report"))
                .thenReturn(List.of("report.pdf", "report(1).pdf"));
        SysFile restored = activeTeamFile(10L, "report(2).pdf", 8L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root, restored);
        when(sysFileMapper.restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 0L, "", "report(2).pdf"))
                .thenReturn(1);

        SysFile result = writeService.restoreFromTrash(TEAM_ID, 10L, ConflictPolicy.RENAME);

        assertThat(result.getOriginalName()).isEqualTo("report(2).pdf");
        verify(sysFileMapper).restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 0L, "", "report(2).pdf");
    }

    @Test
    @DisplayName("恢复团队回收站文件：OVERWRITE 冲突策略先将活动同名文件移入回收站")
    void restoreFromTrash_overwriteConflictMovesActiveTargetToTrash() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        SysFile conflict = activeTeamFile(20L, "report.pdf", 5L);
        SysFile restored = activeTeamFile(10L, "report.pdf", 8L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root, restored);
        when(sysFileMapper.selectTeamActiveByNameInDirectory(TEAM_ID, 0L, "report.pdf")).thenReturn(conflict);
        when(sysFileMapper.moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(20L), eq(USER_ID), anyLong())).thenReturn(1);
        when(sysFileMapper.restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 0L, "", "report.pdf"))
                .thenReturn(1);

        SysFile result = withCurrentUser(() -> writeService.restoreFromTrash(TEAM_ID, 10L, ConflictPolicy.OVERWRITE));

        assertThat(result.getOriginalName()).isEqualTo("report.pdf");
        verify(sysFileMapper).moveRootToTrashInSpace(eq("TEAM"), eq(TEAM_ID), eq(20L), eq(USER_ID), anyLong());
        verify(teamQuotaService).decreaseUsedSpace(TEAM_ID, 5L);
        verify(teamQuotaService).increaseUsedSpace(TEAM_ID, 8L);
        verify(sysFileMapper).restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 0L, "", "report.pdf");
    }

    @Test
    @DisplayName("永久删除团队回收站文件：允许过期根节点并仅释放对象引用")
    void permanentlyDeleteTrash_fileAllowsExpiredRootAndDoesNotChangeQuota() {
        SysFile root = recycledTeamFile(10L, "expired.pdf", 8L);
        root.setFileHash("hash-expired");
        root.setFilePath("202605/expired.pdf");
        root.setExpireAt(LocalDateTime.now().minusDays(10));
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L)).thenReturn(1);

        writeService.permanentlyDeleteTrash(TEAM_ID, 10L);

        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(fileObjectService).decreaseReferenceOrRemove(root);
        verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L);
        verify(teamQuotaService, never()).decreaseUsedSpace(any(), anyLong());
    }

    @Test
    @DisplayName("永久删除团队回收站目录：递归释放后代文件对象并通过 XML 删除整棵树")
    void permanentlyDeleteTrash_directoryDecrementsFileReferencesAndDeletesTreeInMapper() {
        SysFile root = teamDirectory(10L, 0L, "10");
        root.setInRecycleBin(1);
        root.setRecycleRoot(1);
        SysFile childFile = teamFile(11L, 0);
        childFile.setParentId(10L);
        childFile.setFileHash("hash-child");
        childFile.setInRecycleBin(1);
        childFile.setRecycleRoot(0);
        SysFile childDirectory = teamDirectory(12L, 10L, "10,12");
        childDirectory.setInRecycleBin(1);
        childDirectory.setRecycleRoot(0);
        SysFile grandchildFile = teamFile(13L, 0);
        grandchildFile.setParentId(12L);
        grandchildFile.setFileHash("hash-grandchild");
        grandchildFile.setInRecycleBin(1);
        grandchildFile.setRecycleRoot(0);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.selectDescendantsInSpace("TEAM", TEAM_ID, 10L))
                .thenReturn(List.of(childFile, childDirectory, grandchildFile));
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L)).thenReturn(1);

        writeService.permanentlyDeleteTrash(TEAM_ID, 10L);

        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(fileObjectService).decreaseReferenceOrRemove(childFile);
        verify(fileObjectService).decreaseReferenceOrRemove(grandchildFile);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(root);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(childDirectory);
        verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L);
        verify(teamQuotaService, never()).decreaseUsedSpace(any(), anyLong());
    }

    @Test
    @DisplayName("永久删除团队回收站文件：元数据删除失败时不释放对象引用")
    void permanentlyDeleteTrash_doesNotTouchObjectReferencesWhenMetadataDeleteFails() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        root.setFileHash("hash-report");
        root.setFilePath("202605/report.pdf");
        BusinessException failure = new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, "删除元数据失败");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        doThrow(failure).when(sysFileMapper).permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L);

        assertThatThrownBy(() -> writeService.permanentlyDeleteTrash(TEAM_ID, 10L))
                .isSameAs(failure);

        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

}
