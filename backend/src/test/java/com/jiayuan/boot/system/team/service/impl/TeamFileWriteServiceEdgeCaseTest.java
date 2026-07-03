package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.bo.TeamFileBuildBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团队文件变更服务边界分支补充测试。
 *
 * @author charleslam
 * @since 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamFileWriteService 边界补充单元测试")
class TeamFileWriteServiceEdgeCaseTest extends TeamFileWriteServiceTestSupport {

    @Test
    @DisplayName("重命名团队目录：新名称需校验同目录唯一并更新目录")
    void renameDirectory_newNameValidatesUniqueAndUpdatesDirectory() {
        DirectoryRenameRequestVO request = new DirectoryRenameRequestVO();
        request.setName("设计稿-v2");
        SysFile directory = teamDirectory(10L, 0L, "10");
        directory.setOriginalName("设计稿");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(directory);

        SysFile result = writeService.renameDirectory(TEAM_ID, 10L, request);

        assertThat(result.getOriginalName()).isEqualTo("设计稿-v2");
        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 0L);
        verify(sysFileMapper).existsTeamNameInDirectory(TEAM_ID, 0L, "设计稿-v2");
        verify(sysFileMapper).updateById(directory);
    }

    @Test
    @DisplayName("重命名团队目录：名称不变时跳过唯一性检查")
    void renameDirectory_sameNameSkipsUniqueCheck() {
        DirectoryRenameRequestVO request = new DirectoryRenameRequestVO();
        request.setName("设计稿");
        SysFile directory = teamDirectory(10L, 0L, "10");
        directory.setOriginalName("设计稿");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(directory);

        writeService.renameDirectory(TEAM_ID, 10L, request);

        verify(sysFileMapper).lockActiveChildrenInSpace("TEAM", TEAM_ID, 0L);
        verify(sysFileMapper, never()).existsTeamNameInDirectory(anyLong(), anyLong(), any());
        verify(sysFileMapper).updateById(directory);
    }

    @Test
    @DisplayName("重命名团队目录：文件节点按目录不存在处理")
    void renameDirectory_fileNodeRejected() {
        DirectoryRenameRequestVO request = new DirectoryRenameRequestVO();
        request.setName("report-v2.pdf");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(teamFile(10L, 0));

        assertThatThrownBy(() -> writeService.renameDirectory(TEAM_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("上传团队文件：零字节文件不预留或回滚容量")
    void uploadFile_zeroSizeDoesNotReserveOrRollbackQuota() {
        MultipartFile multipartFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);
        StoredFileObjectBO storedObject = storedObject(0L, "hash-empty");
        SysFile savedFile = teamFile(null, 0);
        savedFile.setFileHash("hash-empty");
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toTeamUploadedFile(any(TeamFileBuildBO.class))).thenReturn(savedFile);
        assignInsertedIds(21L);

        SysFile result = withCurrentUser(() -> writeService.uploadFile(TEAM_ID, multipartFile, null));

        assertThat(result.getFullPath()).isEqualTo("21");
        verify(teamQuotaService).checkQuota(TEAM_ID, 0L);
        verify(teamQuotaService, never()).increaseUsedSpace(any(), anyLong());
        verify(teamQuotaService, never()).decreaseUsedSpace(any(), anyLong());
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(StoredFileObjectBO.class));
    }

    @Test
    @DisplayName("移动团队文件：目标父目录相同时拒绝")
    void moveFile_sameParentRejectedBeforeTargetValidation() {
        SysFile source = teamFile(10L, 0);
        source.setParentId(2L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);

        assertThatThrownBy(() -> writeService.moveFile(TEAM_ID, 10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).updateRootLocationInSpace(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("移动团队目录：不能移动到自身子目录")
    void moveFile_directoryToDescendantRejected() {
        SysFile source = teamDirectory(10L, 0L, "10");
        source.setOriginalName("docs");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 12L)).thenReturn(teamDirectory(12L, 10L, "10,12"));

        assertThatThrownBy(() -> writeService.moveFile(TEAM_ID, 10L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).updateRootLocationInSpace(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("复制团队目录：不能复制到自身子目录")
    void copyFile_directoryToDescendantRejectedBeforeQuota() {
        SysFile source = teamDirectory(10L, 0L, "10");
        source.setOriginalName("docs");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 12L)).thenReturn(teamDirectory(12L, 10L, "10,12"));

        assertThatThrownBy(() -> withCurrentUser(() -> writeService.copyFile(TEAM_ID, 10L, 12L)))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(teamQuotaService, never()).checkQuota(any(), anyLong());
        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("恢复团队回收站文件：根节点不存在时拒绝")
    void restoreFromTrash_missingRootRejected() {
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(null);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(sysFileMapper, never()).lockActiveChildrenInSpace(any(), any(), any());
        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：活动文件不能按回收站文件恢复")
    void restoreFromTrash_activeFileRejected() {
        SysFile root = activeTeamFile(10L, "report.pdf", 8L);
        root.setInRecycleBin(0);
        root.setRecycleRoot(1);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：非回收站根节点不能单独恢复")
    void restoreFromTrash_nestedTrashNodeRejected() {
        SysFile root = recycledTeamFile(10L, "nested.pdf", 8L);
        root.setRecycleRoot(0);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复团队回收站文件：恢复行数为 0 时提示状态已变化")
    void restoreFromTrash_zeroRestoredRowsThrowsStateChanged() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.restoreTrashTreeInSpace("TEAM", TEAM_ID, 10L, 0L, "", "report.pdf"))
                .thenReturn(0);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(teamQuotaService).checkQuota(TEAM_ID, 8L);
        verify(teamQuotaService, never()).increaseUsedSpace(any(), anyLong());
    }

    @Test
    @DisplayName("永久删除团队回收站文件：根节点不存在时拒绝")
    void permanentlyDeleteTrash_missingRootRejected() {
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(null);

        assertThatThrownBy(() -> writeService.permanentlyDeleteTrash(TEAM_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(sysFileMapper, never()).permanentlyDeleteTrashTreeInSpace(any(), any(), any());
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    @Test
    @DisplayName("永久删除团队回收站文件：活动文件不能按回收站文件永久删除")
    void permanentlyDeleteTrash_activeFileRejected() {
        SysFile root = activeTeamFile(10L, "report.pdf", 8L);
        root.setInRecycleBin(0);
        root.setRecycleRoot(1);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);

        assertThatThrownBy(() -> writeService.permanentlyDeleteTrash(TEAM_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).permanentlyDeleteTrashTreeInSpace(any(), any(), any());
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    @Test
    @DisplayName("永久删除团队回收站文件：非回收站根节点不能单独永久删除")
    void permanentlyDeleteTrash_nestedTrashNodeRejected() {
        SysFile root = recycledTeamFile(10L, "nested.pdf", 8L);
        root.setRecycleRoot(0);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);

        assertThatThrownBy(() -> writeService.permanentlyDeleteTrash(TEAM_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).permanentlyDeleteTrashTreeInSpace(any(), any(), any());
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    @Test
    @DisplayName("永久删除团队回收站文件：删除行数为 0 时不释放对象引用")
    void permanentlyDeleteTrash_zeroDeletedRowsThrowsStateChanged() {
        SysFile root = recycledTeamFile(10L, "report.pdf", 8L);
        root.setFileHash("hash-report");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("TEAM", TEAM_ID, 10L)).thenReturn(0);

        assertThatThrownBy(() -> writeService.permanentlyDeleteTrash(TEAM_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    @Test
    @DisplayName("解散团队清理文件：空团队空间直接返回")
    void permanentlyDeleteTeamSpaceFiles_emptySpaceReturnsWithoutDelete() {
        when(sysFileMapper.selectFilesInSpace("TEAM", TEAM_ID)).thenReturn(List.of());

        writeService.permanentlyDeleteTeamSpaceFiles(TEAM_ID);

        verify(teamSpaceMapper).lockActiveTeamSpace(TEAM_ID);
        verify(sysFileMapper, never()).permanentlyDeleteSpaceFiles(any(), any());
        verify(fileObjectService, never()).decreaseReferenceOrRemove(any(SysFile.class));
    }

    @Test
    @DisplayName("解散团队清理文件：删除成功后仅释放文件对象引用")
    void permanentlyDeleteTeamSpaceFiles_decrementsOnlyFileReferencesAfterDelete() {
        SysFile directory = teamDirectory(10L, 0L, "10");
        SysFile file = teamFile(11L, 0);
        file.setFileHash("hash-file");
        SysFile nestedFile = teamFile(12L, 0);
        nestedFile.setFileHash("hash-nested");
        when(sysFileMapper.selectFilesInSpace("TEAM", TEAM_ID)).thenReturn(List.of(directory, file, nestedFile));
        when(sysFileMapper.permanentlyDeleteSpaceFiles("TEAM", TEAM_ID)).thenReturn(3);

        writeService.permanentlyDeleteTeamSpaceFiles(TEAM_ID);

        verify(sysFileMapper).permanentlyDeleteSpaceFiles("TEAM", TEAM_ID);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(directory);
        verify(fileObjectService).decreaseReferenceOrRemove(file);
        verify(fileObjectService).decreaseReferenceOrRemove(nestedFile);
    }

}
